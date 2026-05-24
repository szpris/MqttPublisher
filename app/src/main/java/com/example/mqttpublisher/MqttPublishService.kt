package com.example.mqttpublisher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MqttPublishService : Service() {

    companion object {
        const val CHANNEL_ID = "mqtt_publisher_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STATUS_UPDATE = "com.example.mqttpublisher.STATUS_UPDATE"
        const val ACTION_MESSAGE_PUBLISHED = "com.example.mqttpublisher.MESSAGE_PUBLISHED"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_COUNT = "extra_count"
        const val EXTRA_SUCCESS = "extra_success"
        const val MAX_MESSAGES = 20
    }

    private val binder = LocalBinder()
    private var mqttClient: MqttClient? = null
    private var timer: Timer? = null
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val publishCount = AtomicInteger(0)
    private val messages = ArrayDeque<MessageItem>(MAX_MESSAGES + 1)
    private val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())

    inner class LocalBinder : Binder() {
        fun getService(): MqttPublishService = this@MqttPublishService
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val notification = buildNotification("MQTT 发布服务已就绪")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // 防止 startForeground 失败导致闪退
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        stopPublishing()
        super.onDestroy()
    }

    // ==================== 公开接口 ====================

    fun isRunning() = running.get()
    fun isConnected() = connected.get()
    fun getPublishCount() = publishCount.get()

    fun getMessages(): List<MessageItem> = synchronized(messages) { messages.toList() }

    fun clearMessages() = synchronized(messages) { messages.clear() }

    fun startPublishing() {
        if (running.get()) return
        running.set(true)
        publishCount.set(0)
        connectAndSchedule()
        broadcastStatus()
    }

    fun stopPublishing() {
        if (!running.get() && mqttClient == null) return
        running.set(false)
        timer?.cancel()
        timer = null
        disconnectMqtt()
        broadcastStatus()
        updateNotification("已停止")
    }

    // ==================== MQTT ====================

    private fun connectAndSchedule() {
        val prefs = AppPrefs(this)
        Thread {
            try {
                val deviceName = Build.MODEL.replace(" ", "_")
                val timestamp = sdf.format(Date())
                val clientId = "${deviceName}_${timestamp}"

                mqttClient?.disconnect()
                mqttClient = MqttClient(
                    "tcp://${prefs.host}:${prefs.port}",
                    clientId,
                    MemoryPersistence()
                )

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 20
                    isAutomaticReconnect = true
                    if (prefs.username.isNotBlank()) {
                        userName = prefs.username
                        password = prefs.password.toCharArray()
                    }
                }

                mqttClient!!.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        connected.set(false)
                        broadcastStatus()
                        updateNotification("连接断开，重连中...")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {}

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient!!.connect(options)
                connected.set(true)
                broadcastStatus()
                updateNotification("已连接 ${prefs.host}")
                schedulePublish(prefs)
            } catch (e: MqttException) {
                connected.set(false)
                broadcastStatus()
                updateNotification("连接失败: ${e.message}")
            }
        }.start()
    }

    private fun schedulePublish(prefs: AppPrefs) {
        timer?.cancel()
        timer = Timer()
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!running.get()) {
                    cancel()
                    return
                }
                publishMessage()
            }
        }, 0L, prefs.interval * 1000L)
    }

    private fun publishMessage() {
        val prefs = AppPrefs(this)
        val client = mqttClient ?: return
        val count = publishCount.incrementAndGet()
        val timeStr = sdf.format(Date())
        val payload = "${timeStr}_${count}"
        val topic = prefs.topic

        return try {
            val msg = MqttMessage(payload.toByteArray()).apply {
                qos = prefs.qos
                isRetained = prefs.retained
            }
            client.publish(topic, msg)
            connected.set(true)
            addMessage(MessageItem(timeStr, payload, count, true))
            broadcastMessage(payload, timeStr, count, true)
            updateNotification("运行中 | 已发 $count 条")
        } catch (e: MqttException) {
            connected.set(false)
            addMessage(MessageItem(timeStr, "发送失败: ${e.message}", count, false))
            broadcastMessage("发送失败: ${e.message}", timeStr, count, false)
            broadcastStatus()
        }
    }

    private fun disconnectMqtt() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (_: Exception) {}
        mqttClient = null
        connected.set(false)
    }

    // ==================== 消息记录 ====================

    private fun addMessage(item: MessageItem) {
        synchronized(messages) {
            messages.addFirst(item)
            if (messages.size > MAX_MESSAGES) messages.pollLast()
        }
    }

    // ==================== 广播 ====================

    private fun broadcastStatus() {
        sendBroadcast(Intent(ACTION_STATUS_UPDATE).setPackage(packageName))
    }

    private fun broadcastMessage(msg: String, time: String, count: Int, success: Boolean) {
        val intent = Intent(ACTION_MESSAGE_PUBLISHED).apply {
            putExtra(EXTRA_MESSAGE, msg)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_COUNT, count)
            putExtra(EXTRA_SUCCESS, success)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Publisher",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MQTT 消息发布服务"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT 发布器")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ==================== 重新加载配置 ====================

    fun reloadAndRestart() {
        if (running.get()) {
            stopPublishing()
            Thread.sleep(500)
            startPublishing()
        }
    }
}
