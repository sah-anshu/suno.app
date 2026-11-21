// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

/** A place from the phone's own GPS (no internet needed). */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    /** Radius in metres within which the patient probably is. */
    val accuracyMeters: Float,
)

/**
 * The patient asked for help. Sent by the patient's phone to every connected device, again whenever
 * someone answers, and once more with [active] false when the patient is safe.
 */
data class SosAlert(
    /** Changes with each new SOS, so answers to an old one are ignored. */
    val id: Long,
    val active: Boolean,
    val location: GeoPoint? = null,
    /** Names of the people who said they are on their way. */
    val responders: List<String> = emptyList(),
)

/** Answers a companion can send to an SOS. */
object SosReplies {
    const val ON_MY_WAY = 1
}
