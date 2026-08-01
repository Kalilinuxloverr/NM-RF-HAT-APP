package com.nmrf.remote.core

import android.content.Context

/** Persistenter Kleinkram (Disclaimer, zuletzt verbundener HAT). SharedPreferences. */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nmrf", Context.MODE_PRIVATE)

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean(KEY_DISCLAIMER, false)
        set(v) { sp.edit().putBoolean(KEY_DISCLAIMER, v).apply() }

    var lastHat: String?
        get() = sp.getString(KEY_LAST_HAT, null)
        set(v) { sp.edit().putString(KEY_LAST_HAT, v).apply() }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_ok"
        const val KEY_LAST_HAT = "last_hat"
    }
}
