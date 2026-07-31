package com.nmrf.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nmrf.remote.core.AppRoot
import com.nmrf.remote.ui.theme.NmrfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NmrfTheme { AppRoot() }
        }
    }
}
