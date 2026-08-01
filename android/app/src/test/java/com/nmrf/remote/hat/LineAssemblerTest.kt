package com.nmrf.remote.hat

import org.junit.Assert.assertEquals
import org.junit.Test

class LineAssemblerTest {
    private fun b(s: String) = s.toByteArray()

    @Test fun reassembles_fragments_into_lines() {
        val a = LineAssembler()
        assertEquals(emptyList<String>(), a.feed(b("inf")))
        assertEquals(listOf("info", "ok"), a.feed(b("o\r\nok\n")))
        assertEquals(emptyList<String>(), a.feed(b("partial")))
        assertEquals(listOf("partial done"), a.feed(b(" done\n")))
    }
}
