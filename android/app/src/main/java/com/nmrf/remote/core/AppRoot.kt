package com.nmrf.remote.core

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixPanel
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.wifi.WifiAnalyzerScreen
import com.nmrf.remote.wifi.WifiAnalyzerViewModel
import com.nmrf.remote.wifi.WifiScanner

private enum class Tab(val label: String, val glyph: String) {
    WIFI("WLAN", "📶"), BLE("BLE", "🔵"), AUDIO("AUDIO", "🎚"), HAT("HAT", "🛰")
}

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

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = Tab.entries
    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = MatrixPanel, contentColor = MatrixGreen) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        icon = { Text(t.glyph, fontSize = 18.sp) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MatrixGreen,
                            selectedTextColor = MatrixGreen,
                            unselectedIconColor = MatrixTextDim,
                            unselectedTextColor = MatrixTextDim,
                            indicatorColor = MatrixGreenDark,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tabs[tabIndex]) {
                Tab.WIFI -> WifiTab()
                Tab.BLE -> BleTab()
                Tab.AUDIO -> AudioTab()
                Tab.HAT -> HatTab()
            }
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
            initializer { HatViewModel(BleBruceLink(context.applicationContext), BleScanner(context.applicationContext)) }
        },
    )
    HatScreen(vm = vm, hasPermission = perm.allGranted, onRequestPermission = perm.request)
}
