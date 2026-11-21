// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallSettingsTest {
    @Test
    fun `favourites round-trip, keep their order and drop empty entries`() {
        val list =
            listOf(
                Favourite("Rahul", "Son", "+91 98765 43210"),
                Favourite("डॉ. शर्मा", "डॉक्टर", "011-2345\t6789"),
                Favourite("", "", "123"),
            )
        val normalized = CallSettings(list).normalized().favourites
        assertEquals(listOf("Rahul", "डॉ. शर्मा"), normalized.map { it.name })
        assertEquals("+919876543210", normalized[0].number)
        assertEquals(normalized, CallSettings.decodeFavourites(CallSettings.encodeFavourites(normalized)))
        assertTrue(CallSettings.decodeFavourites(null).isEmpty())
    }
}
