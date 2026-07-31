package com.nmrf.remote.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun App() {
    var booted by rememberSaveable { mutableStateOf(false) }
    if (!booted) BootScreen(onDone = { booted = true }) else AppRoot()
}
