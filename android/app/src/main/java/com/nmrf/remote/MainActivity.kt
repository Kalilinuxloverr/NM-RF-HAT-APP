package com.nmrf.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nmrf.remote.ui.theme.NmrfTheme
import com.nmrf.remote.wifi.WifiAnalyzerScreen
import com.nmrf.remote.wifi.WifiAnalyzerViewModel
import com.nmrf.remote.wifi.WifiScanner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        setContent {
            NmrfTheme {
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED,
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { ok -> granted = ok }

                val vm: WifiAnalyzerViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { WifiAnalyzerViewModel(WifiScanner(appContext)) }
                    },
                )

                LaunchedEffect(granted) { if (granted) vm.rescan() }

                WifiAnalyzerScreen(
                    vm = vm,
                    hasPermission = granted,
                    onRequestPermission = {
                        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                )
            }
        }
    }
}
