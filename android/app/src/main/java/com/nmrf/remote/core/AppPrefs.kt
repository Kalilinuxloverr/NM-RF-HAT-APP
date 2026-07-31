package com.nmrf.remote.core

import android.content.Context

/** Kleiner persistenter Zustand (Disclaimer bestätigt). SharedPreferences, keine DataStore-Dep. */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nmrf", Context.MODE_PRIVATE)

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean(KEY_DISCLAIMER, false)
        set(v) { sp.edit().putBoolean(KEY_DISCLAIMER, v).apply() }

    private companion object { const val KEY_DISCLAIMER = "disclaimer_ok" }
}
