// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoredCompanionTest {
    @Test
    fun `companions round-trip and odd names stay on one line`() {
        val list = listOf(StoredCompanion("a1", "Rahul's phone"), StoredCompanion("b2", "नाना\tका\nफ़ोन"))
        val decoded = StoredCompanion.decode(StoredCompanion.encode(list))
        assertEquals(listOf("a1", "b2"), decoded.map { it.id })
        assertEquals("नाना का फ़ोन", decoded[1].name)
        assertTrue(StoredCompanion.decode(null).isEmpty())
        assertTrue(StoredCompanion.decode("garbage-without-tab").isEmpty())
    }
}
