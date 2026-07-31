package com.nmrf.remote.ble

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerViewModelTest {

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun dev(addr: String, rssi: Int, conn: Boolean, mfr: String?) = BleDevice(
        address = addr, name = "n", rssi = rssi, connectable = conn, txPower = null,
        companyId = null, manufacturer = mfr, serviceUuids = emptyList(),
        rawBytes = ByteArray(0), lastSeen = 0L, rssiHistory = listOf(rssi),
    )

    private class Fake(list: List<BleDevice>) : BleSource {
        override val devices: Flow<List<BleDevice>> = flowOf(list)
        override fun isReady() = true
    }

    @Test fun sorts_by_rssi() = runTest {
        val vm = BleScannerViewModel(
            Fake(listOf(dev("AA", -80, true, "Apple"), dev("BB", -40, false, "Google"), dev("CC", -60, true, "Apple"))),
        )
        vm.setEnabled(true); advanceUntilIdle()
        assertEquals(listOf(-40, -60, -80), vm.state.value.devices.map { it.rssi })
    }

    @Test fun connectable_and_text_filter() = runTest {
        val vm = BleScannerViewModel(
            Fake(listOf(dev("AA", -80, true, "Apple"), dev("BB", -40, false, "Google"), dev("CC", -60, true, "Apple"))),
        )
        vm.setEnabled(true)
        vm.setConnectableOnly(true); advanceUntilIdle()
        assertEquals(listOf(-60, -80), vm.state.value.devices.map { it.rssi })   // BB (non-conn) raus

        vm.setConnectableOnly(false); vm.setFilter("google"); advanceUntilIdle()
        assertEquals(listOf("BB"), vm.state.value.devices.map { it.address })
    }
}
