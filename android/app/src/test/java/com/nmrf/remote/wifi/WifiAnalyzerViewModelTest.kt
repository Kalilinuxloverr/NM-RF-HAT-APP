package com.nmrf.remote.wifi

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
class WifiAnalyzerViewModelTest {

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun ap(rssi: Int, band: Band) = AccessPoint(
        ssid = "n$rssi",
        bssid = "00:00",
        freqMhz = if (band == Band.GHZ_5) 5180 else 2412,
        rssi = rssi,
        channel = 1,
        band = band,
        widthMhz = 20,
    )

    private class Fake(val list: List<AccessPoint>) : WifiSource {
        override val results: Flow<List<AccessPoint>> = flowOf(list)
        override fun requestScan() = true
        override fun latest() = list
    }

    @Test
    fun sorts_by_rssi_and_filters_band() = runTest {
        val src = Fake(listOf(ap(-70, Band.GHZ_2_4), ap(-40, Band.GHZ_2_4), ap(-50, Band.GHZ_5)))
        val vm = WifiAnalyzerViewModel(src)
        vm.selectBand(Band.GHZ_2_4)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf(-40, -70), s.visible.map { it.rssi })   // sortiert, 5-GHz-AP gefiltert
        assertEquals(Band.GHZ_2_4, s.selectedBand)
    }

    @Test
    fun rescan_reflects_source_result() = runTest {
        val vm = WifiAnalyzerViewModel(Fake(emptyList()))
        vm.rescan()
        advanceUntilIdle()
        assertEquals(true, vm.state.value.scanning)   // Fake.requestScan() == true
    }
}
