package com.nmrf.remote.core

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class PermissionState(val allGranted: Boolean, val request: () -> Unit)

/** Runtime-Permissions für Compose: prüft aktuellen Stand + liefert request()-Trigger. */
@Composable
fun rememberPermissions(perms: List<String>): PermissionState {
    val context = LocalContext.current
    fun check() = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = check() }
    return PermissionState(granted) { launcher.launch(perms.toTypedArray()) }
}
