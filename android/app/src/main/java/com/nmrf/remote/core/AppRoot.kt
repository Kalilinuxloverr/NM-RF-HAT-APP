package com.nmrf.remote.core

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nmrf.remote.ble.BleScanner
import com.nmrf.remote.ble.BleScannerScreen
import com.nmrf.remote.ble.BleScannerViewModel
import com.nmrf.remote.ble.GattProbe
import com.nmrf.remote.wifi.WifiAnalyzerScreen
import com.nmrf.remote.wifi.WifiAnalyzerViewModel
import com.nmrf.remote.wifi.WifiScanner

private enum class Tab(val label: String, val glyph: String) {
    WIFI("WLAN", "📶"), BLE("BLE", "🔵"), AUDIO("Audio", "🎙"), HAT("HAT", "🛰")
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
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        icon = { Text(t.glyph, fontSize = 18.sp) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tabs[tabIndex]) {
                Tab.WIFI -> WifiTab()
                Tab.BLE -> BleTab()
                Tab.AUDIO -> Placeholder("Audio-Spektrogramm — kommt (Modul 3)")
                Tab.HAT -> Placeholder("HAT-Fernsteuerung — kommt (Modul 4/5)")
            }
        }
    }
}

@Composable
private fun WifiTab() {
    val context = LocalContext.current
    val perm = rememberPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    val vm: WifiAnalyzerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WifiAnalyzerViewModel(WifiScanner(context.applicationContext)) }
        },
    )
    LaunchedEffect(perm.allGranted) { if (perm.allGranted) vm.rescan() }
    WifiAnalyzerScreen(vm = vm, hasPermission = perm.allGranted, onRequestPermission = perm.request)
}

@Composable
private fun BleTab() {
    val context = LocalContext.current
    val blePerms = remember {
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val perm = rememberPermissions(blePerms)
    val vm: BleScannerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BleScannerViewModel(BleScanner(context.applicationContext)) }
        },
    )
    val gattProbe = remember { GattProbe(context.applicationContext) }
    LaunchedEffect(perm.allGranted) { vm.setEnabled(perm.allGranted) }
    BleScannerScreen(
        vm = vm,
        hasPermission = perm.allGranted,
        onRequestPermission = perm.request,
        gattProbe = gattProbe,
    )
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}
