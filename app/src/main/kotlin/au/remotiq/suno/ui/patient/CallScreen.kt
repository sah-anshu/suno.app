// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.remotiq.suno.R
import au.remotiq.suno.phone.CallPhase
import au.remotiq.suno.phone.CallRoute
import au.remotiq.suno.phone.CallUi
import kotlinx.coroutines.delay

/** Incoming and in-call screens: tap buttons only, words with every icon, a warning for unknown callers. */
@Composable
fun CallScreen(
    call: CallUi,
    callbacks: CallCallbacks,
    now: () -> Long = System::currentTimeMillis,
) {
    val c = LocalPatientColors.current
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CallerHeader(call, now)
            when (call.phase) {
                CallPhase.RINGING -> Incoming(call, callbacks)
                CallPhase.ENDED -> Unit
                else -> InCall(call, callbacks)
            }
        }
    }
}

@Composable
private fun CallerHeader(
    call: CallUi,
    now: () -> Long,
) {
    val c = LocalPatientColors.current
    val name = call.caller.name ?: call.caller.number.ifBlank { stringResource(R.string.call_hidden_number) }
    Box(Modifier.size(120.dp).background(c.on, CircleShape), contentAlignment = Alignment.Center) {
        if (call.caller.known) {
            Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = c.onInk)
        } else {
            Icon(Icons.Filled.Call, null, tint = c.onInk, modifier = Modifier.size(56.dp))
        }
    }
    Text(name, style = MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center)
    call.caller.relationship?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = c.muted) }
    if (call.caller.known && call.caller.name != call.caller.number) {
        Text(call.caller.number, style = MaterialTheme.typography.bodyLarge, color = c.muted)
    }
    Text(statusText(call, now), style = MaterialTheme.typography.titleLarge, color = c.ink)
}

@Composable
private fun statusText(
    call: CallUi,
    now: () -> Long,
): String =
    when (call.phase) {
        CallPhase.RINGING -> stringResource(R.string.call_is_calling)
        CallPhase.DIALING -> stringResource(R.string.call_calling)
        CallPhase.HOLDING -> stringResource(R.string.call_on_hold)
        CallPhase.ENDED -> stringResource(R.string.call_ended)
        CallPhase.ACTIVE -> {
            var tick by remember { mutableLongStateOf(now()) }
            LaunchedEffect(call.connectedAt) {
                while (true) {
                    tick = now()
                    delay(SECOND_MS)
                }
            }
            val seconds = ((tick - (call.connectedAt ?: tick)) / SECOND_MS).coerceAtLeast(0)
            stringResource(R.string.call_duration, seconds / MINUTE, seconds % MINUTE)
        }
    }

@Composable
private fun Incoming(
    call: CallUi,
    callbacks: CallCallbacks,
) {
    val c = LocalPatientColors.current
    if (!call.caller.known) {
        Row(
            Modifier.fillMaxWidth().background(c.warn, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, null, tint = c.warnInk, modifier = Modifier.size(32.dp))
            Text(
                stringResource(R.string.call_unknown_warning),
                style = MaterialTheme.typography.titleMedium,
                color = c.warnInk,
            )
        }
    }
    BigCallButton(Icons.Filled.Call, stringResource(R.string.call_answer), c.help, c.helpInk, callbacks.onAnswer)
    BigCallButton(Icons.Filled.CallEnd, stringResource(R.string.call_decline), c.sos, c.sosInk, callbacks.onDecline)
}

@Composable
private fun InCall(
    call: CallUi,
    callbacks: CallCallbacks,
) {
    val c = LocalPatientColors.current
    PatientCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.call_sound_to), style = MaterialTheme.typography.labelLarge, color = c.muted)
        CallRoute.entries.filter { it in call.routes }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { route ->
                    RouteChip(route, route == call.route, Modifier.weight(1f)) { callbacks.onRoute(route) }
                }
            }
        }
    }
    if (call.maxVolume > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RoundIconButton(Icons.AutoMirrored.Filled.VolumeDown, stringResource(R.string.call_quieter), {
                callbacks.onVolume(false)
            }, size = 72)
            Text(
                stringResource(R.string.call_volume, call.volume, call.maxVolume),
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            RoundIconButton(
                Icons.AutoMirrored.Filled.VolumeUp,
                stringResource(R.string.call_louder),
                { callbacks.onVolume(true) },
                size = 72,
            )
        }
    }
    WideToggle(
        icon = if (call.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
        label = stringResource(if (call.muted) R.string.call_unmute else R.string.call_mute),
        on = call.muted,
    ) { callbacks.onMute(!call.muted) }
    BigCallButton(Icons.Filled.CallEnd, stringResource(R.string.call_end), c.sos, c.sosInk, callbacks.onEnd)
}

@Composable
private fun RouteChip(
    route: CallRoute,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    val (icon, label) =
        when (route) {
            CallRoute.PHONE -> Icons.Filled.PhoneAndroid to R.string.call_route_phone
            CallRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp to R.string.call_route_speaker
            CallRoute.EARPHONES -> Icons.Filled.Headphones to R.string.call_route_earphones
            CallRoute.BLUETOOTH -> Icons.Filled.Bluetooth to R.string.call_route_bluetooth
        }
    val selectedText = stringResource(R.string.patient_selected)
    Row(
        modifier
            .heightIn(min = 64.dp)
            .background(if (selected) c.on else c.background, RoundedCornerShape(18.dp))
            .border(2.dp, if (selected) c.on else c.hairline, RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            if (selected) Icons.Filled.Check else icon,
            null,
            tint = if (selected) c.onInk else c.ink,
            modifier = Modifier.size(24.dp),
        )
        Text(
            stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) c.onInk else c.ink,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WideToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (on) c.warn else c.surface, RoundedCornerShape(22.dp))
            .border(2.dp, if (on) c.warn else c.hairline, RoundedCornerShape(22.dp))
            .selectable(selected = on, role = Role.Switch, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = if (on) c.warnInk else c.ink, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (on) c.warnInk else c.ink)
    }
}

@Composable
internal fun BigCallButton(
    icon: ImageVector,
    label: String,
    color: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Surface(
        color = color,
        contentColor = ink,
        shape = RoundedCornerShape(26.dp),
        border = if (c.outlined) BorderStroke(3.dp, c.ink) else null,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .selectable(selected = false, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            Icon(icon, null, modifier = Modifier.size(36.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private const val SECOND_MS = 1_000L
private const val MINUTE = 60L
