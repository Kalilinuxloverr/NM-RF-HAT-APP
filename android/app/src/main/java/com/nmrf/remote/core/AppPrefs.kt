package com.nmrf.remote.core

import android.content.Context

class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nmrf", Context.MODE_PRIVATE)

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean("disclaimer_ok", false)
        set(v) { sp.edit().putBoolean("disclaimer_ok", v).apply() }

    var lastHat: String?
        get() = sp.getString("last_hat", null)
        set(v) { sp.edit().putString("last_hat", v).apply() }

    var transport: String   // "auto" | "wifi" | "ble"
        get() = sp.getString("transport", "auto") ?: "auto"
        set(v) { sp.edit().putString("transport", v).apply() }

    var autoReconnect: Boolean
        get() = sp.getBoolean("auto_reconnect", true)
        set(v) { sp.edit().putBoolean("auto_reconnect", v).apply() }
}
