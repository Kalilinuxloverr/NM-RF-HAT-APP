package com.nmrf.remote.hat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkerTest {
    @Test fun single_chunk_when_short() {
        val c = Chunker.chunks("info", 23)
        assertEquals(1, c.size)
        assertEquals("info\n", String(c[0]))
    }

    @Test fun splits_long_command() {
        val cmd = "a".repeat(50)
        val c = Chunker.chunks(cmd, 23)
        assertEquals(3, c.size)
        assertEquals(cmd + "\n", c.joinToString("") { String(it) })
        assertEquals(20, c[0].size)
    }
}
