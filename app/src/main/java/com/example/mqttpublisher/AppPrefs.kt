package com.example.mqttpublisher

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mqtt_prefs", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "xy.nasi.cn") ?: "xy.nasi.cn"
        set(value) = prefs.edit().putString("host", value).apply()

    var port: Int
        get() = prefs.getInt("port", 51882)
        set(value) = prefs.edit().putInt("port", value).apply()

    var topic: String
        get() = prefs.getString("topic", "SM09aZ09aZ/TestHost/info") ?: "SM09aZ09aZ/TestHost/info"
        set(value) = prefs.edit().putString("topic", value).apply()

    var interval: Long
        get() = prefs.getLong("interval", 5L)
        set(value) = prefs.edit().putLong("interval", value).apply()

    var qos: Int
        get() = prefs.getInt("qos", 1)
        set(value) = prefs.edit().putInt("qos", value).apply()

    var retained: Boolean
        get() = prefs.getBoolean("retained", false)
        set(value) = prefs.edit().putBoolean("retained", value).apply()

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()
}
