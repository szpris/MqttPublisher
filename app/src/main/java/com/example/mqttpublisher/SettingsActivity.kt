package com.example.mqttpublisher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.mqttpublisher.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPrefs
    private var mqttService: MqttPublishService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            mqttService = (binder as MqttPublishService.LocalBinder).getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            mqttService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPrefs(this)
        bindService(Intent(this, MqttPublishService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        loadSettings()
        setupIntervalSeekBar()
        setupSaveButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSettings() {
        binding.etHost.setText(prefs.host)
        binding.etPort.setText(prefs.port.toString())
        binding.etTopic.setText(prefs.topic)
        binding.etUsername.setText(prefs.username)
        binding.etPassword.setText(prefs.password)
        binding.spinnerQos.setSelection(prefs.qos)
        binding.switchRetained.isChecked = prefs.retained

        val interval = prefs.interval.coerceIn(1L, 3600L)
        binding.seekbarInterval.progress = (interval - 1).toInt()
        updateIntervalLabel(interval)
    }

    private fun setupIntervalSeekBar() {
        // SeekBar range: 0~3599 → interval 1~3600 秒
        binding.seekbarInterval.max = 3599
        binding.seekbarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val seconds = (progress + 1).toLong()
                updateIntervalLabel(seconds)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // 手动输入框直接应用到 SeekBar
        binding.btnManualInterval.setOnClickListener {
            val input = binding.etIntervalManual.text.toString().trim()
            val seconds = input.toLongOrNull()
            if (seconds == null || seconds < 1 || seconds > 3600) {
                binding.etIntervalManual.error = "请输入 1~3600 之间的整数"
            } else {
                binding.seekbarInterval.progress = (seconds - 1).toInt()
                updateIntervalLabel(seconds)
            }
        }
    }

    private fun updateIntervalLabel(seconds: Long) {
        val text = when {
            seconds < 60 -> "${seconds}秒"
            seconds % 3600 == 0L -> "${seconds / 3600}小时"
            seconds % 60 == 0L -> "${seconds / 60}分钟"
            else -> "${seconds}秒 (${seconds / 60}分${seconds % 60}秒)"
        }
        binding.tvIntervalValue.text = text
        binding.etIntervalManual.setText(seconds.toString())
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            if (!validateAndSave()) return@setOnClickListener
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            // 如果服务正在运行，询问是否立即生效
            val service = mqttService
            if (service != null && service.isRunning()) {
                AlertDialog.Builder(this)
                    .setTitle("重新连接")
                    .setMessage("服务正在运行，是否立即重新连接以应用新设置？")
                    .setPositiveButton("立即生效") { _, _ ->
                        service.reloadAndRestart()
                        finish()
                    }
                    .setNegativeButton("稍后") { _, _ -> finish() }
                    .show()
            } else {
                finish()
            }
        }
    }

    private fun validateAndSave(): Boolean {
        val host = binding.etHost.text.toString().trim()
        val portStr = binding.etPort.text.toString().trim()
        val topic = binding.etTopic.text.toString().trim()

        if (host.isEmpty()) {
            binding.etHost.error = "服务器地址不能为空"
            return false
        }
        val port = portStr.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            binding.etPort.error = "请输入有效端口 (1-65535)"
            return false
        }
        if (topic.isEmpty()) {
            binding.etTopic.error = "Topic 不能为空"
            return false
        }

        prefs.host = host
        prefs.port = port
        prefs.topic = topic
        prefs.username = binding.etUsername.text.toString().trim()
        prefs.password = binding.etPassword.text.toString()
        prefs.qos = binding.spinnerQos.selectedItemPosition
        prefs.retained = binding.switchRetained.isChecked
        prefs.interval = (binding.seekbarInterval.progress + 1).toLong()
        return true
    }
}
