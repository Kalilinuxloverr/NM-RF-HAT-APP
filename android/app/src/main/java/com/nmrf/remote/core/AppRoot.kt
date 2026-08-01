package com.nmrf.remote.core

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nmrf.remote.audio.AudioSpectrogramScreen
import com.nmrf.remote.audio.AudioSpectrogramViewModel
import com.nmrf.remote.audio.MicAudioSource
import com.nmrf.remote.ble.BleScanner
import com.nmrf.remote.ble.BleScannerScreen
import com.nmrf.remote.ble.BleScannerViewModel
import com.nmrf.remote.ble.GattProbe
import com.nmrf.remote.hat.BleBruceLink
import com.nmrf.remote.hat.HatScreen
import com.nmrf.remote.hat.HatViewModel
import com.nmrf.remote.wifi.WifiAnalyzerScreen
import com.nmrf.remote.wifi.WifiAnalyzerViewModel
import com.nmrf.remote.wifi.WifiScanner

enum class Screen { HOME, WIFI, BLE, AUDIO, HAT, TOOLS, SETTINGS }

private fun blePermsFor(): List<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var accepted by rememberSaveable { mutableStateOf(prefs.disclaimerAccepted) }
    if (!accepted) {
        DisclaimerScreen(onAccept = { prefs.disclaimerAccepted = true; accepted = true })
        return
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    if (screen != Screen.HOME) BackHandler { screen = Screen.HOME }

    Box(Modifier.fillMaxSize().appBackground()) {
        when (screen) {
            Screen.HOME -> LauncherScreen(onOpen = { screen = it })
            Screen.WIFI -> WifiTab()
            Screen.BLE -> BleTab()
            Screen.AUDIO -> AudioTab()
            Screen.HAT -> HatTab()
            Screen.TOOLS -> ToolsScreen(onBack = { screen = Screen.HOME })
            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
        }
    }
}

@Composable
private fun WifiTab() {
    val context = LocalContext.current
    val perm = rememberPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    val vm: WifiAnalyzerViewModel = viewModel(
        factory = viewModelFactory { initializer { WifiAnalyzerViewModel(WifiScanner(context.applicationContext)) } },
    )
    LaunchedEffect(perm.allGranted) { if (perm.allGranted) vm.rescan() }
    WifiAnalyzerScreen(vm = vm, hasPermission = perm.allGranted, onRequestPermission = perm.request)
}

@Composable
private fun BleTab() {
    val context = LocalContext.current
    val perm = rememberPermissions(remember { blePermsFor() })
    val vm: BleScannerViewModel = viewModel(
        factory = viewModelFactory { initializer { BleScannerViewModel(BleScanner(context.applicationContext)) } },
    )
    val gattProbe = remember { GattProbe(context.applicationContext) }
    LaunchedEffect(perm.allGranted) { vm.setEnabled(perm.allGranted) }
    BleScannerScreen(vm = vm, hasPermission = perm.allGranted, onRequestPermission = perm.request, gattProbe = gattProbe)
}

@Composable
private fun AudioTab() {
    val perm = rememberPermissions(listOf(Manifest.permission.RECORD_AUDIO))
    val vm: AudioSpectrogramViewModel = viewModel(
        factory = viewModelFactory { initializer { AudioSpectrogramViewModel(MicAudioSource()) } },
    )
    LaunchedEffect(perm.allGranted) { vm.setEnabled(perm.allGranted) }
    AudioSpectrogramScreen(vm = vm, hasPermission = perm.allGranted, onRequestPermission = perm.request)
}

@Composable
private fun HatTab() {
    val context = LocalContext.current
    val perm = rememberPermissions(remember { blePermsFor() })
    val vm: HatViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HatViewModel(BleBruceLink(context.applicationContext), BleScanner(context.applicationContext), AppPrefs(context.applicationContext)) }
        },
    )
    HatScreen(vm = vm, hasPermission = perm.allGranted, onRequestPermission = perm.request)
}
