package com.example.mqttpublisher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.content.pm.PackageManager
import android.Manifest
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mqttpublisher.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mqttService: MqttPublishService? = null
    private var isBound = false
    private val msgAdapter = MessageAdapter()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MqttPublishService.LocalBinder
            mqttService = localBinder.getService()
            isBound = true
            // 恢复已有消息记录
            msgAdapter.setMessages(mqttService!!.getMessages())
            updateStatusUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mqttService = null
            isBound = false
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                MqttPublishService.ACTION_STATUS_UPDATE -> updateStatusUI()
                MqttPublishService.ACTION_MESSAGE_PUBLISHED -> {
                    val msg = intent.getStringExtra(MqttPublishService.EXTRA_MESSAGE) ?: return
                    val time = intent.getStringExtra(MqttPublishService.EXTRA_TIME) ?: ""
                    val count = intent.getIntExtra(MqttPublishService.EXTRA_COUNT, 0)
                    val success = intent.getBooleanExtra(MqttPublishService.EXTRA_SUCCESS, true)
                    msgAdapter.addMessage(MessageItem(time, msg, count, success))
                    binding.rvMessages.scrollToPosition(0)
                    updateStatusUI()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // RecyclerView
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = msgAdapter

        // 启动/停止按钮
        binding.btnToggle.setOnClickListener {
            val service = mqttService ?: return@setOnClickListener
            if (service.isRunning()) {
                service.stopPublishing()
            } else {
                service.startPublishing()
            }
            updateStatusUI()
        }

        // 清空记录
        binding.btnClear.setOnClickListener {
            msgAdapter.clearMessages()
            mqttService?.clearMessages()
        }

        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        // 注册广播
        val filter = IntentFilter().apply {
            addAction(MqttPublishService.ACTION_STATUS_UPDATE)
            addAction(MqttPublishService.ACTION_MESSAGE_PUBLISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            } else {
                startAndBindService()
            }
        } else {
            startAndBindService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            startAndBindService()
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, MqttPublishService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateStatusUI() {
        val service = mqttService ?: run {
            binding.tvStatus.text = "服务未连接"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            binding.btnToggle.text = "开始发布"
            return
        }

        val prefs = AppPrefs(this)
        binding.tvBroker.text = "服务器: ${prefs.host}:${prefs.port}"
        binding.tvTopic.text = "Topic: ${prefs.topic}"
        binding.tvInterval.text = "间隔: ${prefs.interval}秒"
        binding.tvPublishCount.text = "累计发送: ${service.getPublishCount()} 条"

        when {
            service.isRunning() && service.isConnected() -> {
                binding.tvStatus.text = "● 运行中 - 已连接"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.btnToggle.text = "停止发布"
            }
            service.isRunning() && !service.isConnected() -> {
                binding.tvStatus.text = "◌ 运行中 - 连接中..."
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                binding.btnToggle.text = "停止发布"
            }
            else -> {
                binding.tvStatus.text = "■ 已停止"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                binding.btnToggle.text = "开始发布"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }
}
