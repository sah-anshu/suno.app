// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.sos

import au.remotiq.suno.link.GeoPoint
import java.util.Locale

/** The text sent to the helper's chosen numbers. Plain words, works on any phone. */
object SosText {
    fun build(
        patientName: String,
        location: GeoPoint?,
    ): String {
        val who = patientName.ifBlank { "Suno" }
        val where =
            location?.let {
                val lat = String.format(Locale.ROOT, "%.5f", it.latitude)
                val lon = String.format(Locale.ROOT, "%.5f", it.longitude)
                " Location (about ${it.accuracyMeters.toInt()} m): $lat,$lon https://maps.google.com/?q=$lat,$lon"
            } ?: " Location not available."
        return "SOS from $who. I need help.$where Sent by Suno."
    }
}
