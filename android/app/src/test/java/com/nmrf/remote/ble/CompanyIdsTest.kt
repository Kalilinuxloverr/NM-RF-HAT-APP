package com.nmrf.remote.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanyIdsTest {
    @Test fun known_ids() {
        assertEquals("Apple", CompanyIds.name(0x004C))
        assertEquals("Google", CompanyIds.name(0x00E0))
        assertEquals("Huawei", CompanyIds.name(0x0157))
    }

    @Test fun unknown_and_null() {
        assertNull(CompanyIds.name(0x9999))
        assertNull(CompanyIds.name(null))
    }
}
