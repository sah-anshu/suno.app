// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.sos

import au.remotiq.suno.data.SosSettings
import au.remotiq.suno.link.CompanionDevice
import au.remotiq.suno.link.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SosPhase { IDLE, ASKING, ACTIVE }

/** What the SOS screen shows. Every step has a word and an icon as well as colour. */
data class SosState(
    val phase: SosPhase = SosPhase.IDLE,
    val settings: SosSettings = SosSettings(),
    val locating: Boolean = false,
    val location: GeoPoint? = null,
    /** Phones (and TVs) connected right now, so told at once. */
    val alerted: Int = 0,
    /** People who answered "I'm on my way". */
    val responders: List<String> = emptyList(),
    val helperCalled: Boolean = false,
    val textsSent: Int = 0,
    /** Texts could not be sent automatically; the patient taps to open the messaging app. */
    val textsNeedTap: Boolean = false,
    val alarmOn: Boolean = false,
)

/** What SOS does on the phone. Android-specific, replaced by a fake in tests. */
interface SosActions {
    /** Sends a text to each number without opening an app. False if not allowed (no permission). */
    fun sendTexts(
        numbers: List<String>,
        body: String,
    ): Boolean

    /** Opens the messaging app with the text filled in. */
    fun openTexts(
        numbers: List<String>,
        body: String,
    )

    /** Calls the number straight away. */
    fun call(number: String)

    /** Opens the phone app with the number filled in (emergency numbers cannot be called directly). */
    fun dial(number: String)

    /** The phone's GPS place, or null within about 20 s. Works without internet. */
    suspend fun locate(): GeoPoint?

    /** Speaker-only alarm. Returns false (silent) when no built-in speaker is available. */
    fun alarm(on: Boolean): Boolean

    /** No amplified sound during SOS: stops listening. */
    fun stopListening()

    /** Keeps Suno running in the background while SOS is on (foreground notification). */
    fun keepRunning(on: Boolean)
}

/** The family link as SOS needs it. */
interface SosLink {
    val connected: StateFlow<List<CompanionDevice>>
    val sosResponders: StateFlow<List<String>>

    fun sendSos(
        id: Long,
        location: GeoPoint?,
    )

    fun endSos()
}

/**
 * The patient's SOS: one tap opens a calm question (no countdown); Yes alerts every device in the
 * mesh, finds the place, texts the helper's numbers, calls the helper and offers the emergency
 * number. Ends only when the patient taps "I'm safe now".
 */
class SosController(
    private val scope: CoroutineScope,
    /** The helper's SOS settings and the patient's display name, read when SOS starts. */
    private val loadSettings: suspend () -> Pair<SosSettings, String>,
    private val actions: SosActions,
    private val link: SosLink,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(SosState())
    val state: StateFlow<SosState> = _state.asStateFlow()
    private var watch: Job? = null
    private var id = 0L
    private var patientName = ""

    /** The SOS button: asks first. */
    fun ask() {
        if (_state.value.phase == SosPhase.IDLE) _state.update { it.copy(phase = SosPhase.ASKING) }
    }

    /** "No, go back". */
    fun cancel() {
        if (_state.value.phase == SosPhase.ASKING) _state.value = SosState()
    }

    /** "Yes, get help". */
    fun confirm() {
        if (_state.value.phase != SosPhase.ASKING) return
        id = clock()
        _state.update { it.copy(phase = SosPhase.ACTIVE, alerted = link.connected.value.size) }
        actions.keepRunning(true)
        actions.stopListening()
        link.sendSos(id, null)
        watch =
            scope.launch {
                launch { link.sosResponders.collect { r -> _state.update { it.copy(responders = r) } } }
                launch {
                    link.connected.collect { c ->
                        _state.update { it.copy(alerted = maxOf(it.alerted, c.size)) }
                    }
                }
                val (settings, name) = loadSettings()
                patientName = name
                run(settings)
            }
    }

    private suspend fun run(settings: SosSettings) {
        _state.update { it.copy(settings = settings) }
        if (settings.alarmOnStart) setAlarm(true)
        if (settings.hasHelper) {
            actions.call(settings.helperNumber)
            _state.update { it.copy(helperCalled = true) }
        }
        val location =
            if (settings.sendLocation) {
                _state.update { it.copy(locating = true) }
                actions.locate().also { found -> _state.update { it.copy(locating = false, location = found) } }
            } else {
                null
            }
        if (location != null && _state.value.phase == SosPhase.ACTIVE) link.sendSos(id, location)
        if (settings.textNumbers.isNotEmpty() && _state.value.phase == SosPhase.ACTIVE) {
            val sent = actions.sendTexts(settings.textNumbers, textBody(location))
            _state.update {
                it.copy(textsSent = if (sent) settings.textNumbers.size else 0, textsNeedTap = !sent)
            }
        }
    }

    fun callHelper() {
        val s = _state.value.settings
        if (s.hasHelper) actions.call(s.helperNumber)
        _state.update { it.copy(helperCalled = true) }
    }

    fun callEmergency() = actions.dial(_state.value.settings.emergencyNumber)

    /** Opens the messaging app when texts could not be sent automatically. */
    fun openTexts() {
        val s = _state.value
        actions.openTexts(s.settings.textNumbers, textBody(s.location))
    }

    fun setAlarm(on: Boolean) {
        val playing = on && actions.alarm(true)
        if (!on) actions.alarm(false)
        _state.update { it.copy(alarmOn = playing) }
    }

    /** "I'm safe now": everyone is told, the alarm stops. */
    fun end() {
        if (_state.value.phase != SosPhase.ACTIVE) return
        watch?.cancel()
        actions.alarm(false)
        link.endSos()
        actions.keepRunning(false)
        _state.value = SosState()
    }

    private fun textBody(location: GeoPoint?): String = SosText.build(patientName, location)
}
