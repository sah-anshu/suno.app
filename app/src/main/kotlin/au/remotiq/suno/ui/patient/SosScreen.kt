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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.remotiq.suno.R
import au.remotiq.suno.sos.SosPhase
import au.remotiq.suno.sos.SosState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The octagon used for SOS in every Suno app, so it is recognised by shape as well as colour. */
val OctagonShape: Shape =
    GenericShape { size, _ ->
        val r = minOf(size.width, size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        repeat(8) { i ->
            val angle = PI / 8 + i * PI / 4
            val x = centre.x + r * cos(angle).toFloat()
            val y = centre.y + r * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** The SOS button on Home: a vermillion octagon with the word SOS. */
@Composable
fun SosButton(
    onClick: () -> Unit,
    size: Int = 64,
) {
    val c = LocalPatientColors.current
    val label = stringResource(R.string.sos_button)
    Box(
        Modifier
            .size(size.dp)
            .background(c.sos, OctagonShape)
            .border(if (c.outlined) 3.dp else 0.dp, if (c.outlined) c.ink else c.sos, OctagonShape)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("SOS", style = MaterialTheme.typography.titleMedium, color = c.sosInk)
    }
}

/** Actions from the SOS screens. */
class SosCallbacks(
    val onYes: () -> Unit = {},
    val onNo: () -> Unit = {},
    val onCallHelper: () -> Unit = {},
    val onCallEmergency: () -> Unit = {},
    val onSendTexts: () -> Unit = {},
    val onAlarm: (Boolean) -> Unit = {},
    val onSafe: () -> Unit = {},
)

/** Ask first (no countdown), then show each step of getting help. */
@Composable
fun SosScreen(
    state: SosState,
    callbacks: SosCallbacks,
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
            if (state.phase == SosPhase.ASKING) AskContent(callbacks) else ActiveContent(state, callbacks)
        }
    }
}

@Composable
private fun AskContent(callbacks: SosCallbacks) {
    val c = LocalPatientColors.current
    SosButton(onClick = callbacks.onYes, size = 140)
    Text(
        stringResource(R.string.sos_ask_title),
        style = MaterialTheme.typography.headlineMedium,
        color = c.ink,
        textAlign = TextAlign.Center,
    )
    Text(
        stringResource(R.string.sos_ask_body),
        style = MaterialTheme.typography.bodyLarge,
        color = c.muted,
        textAlign = TextAlign.Center,
    )
    SosWideButton(stringResource(R.string.sos_yes), callbacks.onYes)
    WideButton(stringResource(R.string.sos_no), callbacks.onNo)
}

@Composable
private fun ActiveContent(
    state: SosState,
    callbacks: SosCallbacks,
) {
    val c = LocalPatientColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(64.dp).background(c.sos, OctagonShape), contentAlignment = Alignment.Center) {
            Text("SOS", style = MaterialTheme.typography.titleMedium, color = c.sosInk)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.sos_active_title),
                style = MaterialTheme.typography.headlineMedium,
                color = c.ink,
            )
            Text(stringResource(R.string.sos_active_body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
        }
    }
    state.responders.forEach { name ->
        StepRow(Icons.Filled.CheckCircle, stringResource(R.string.sos_on_the_way, name), strong = true)
    }
    PatientCard(Modifier.fillMaxWidth()) {
        val family =
            if (state.alerted > 0) {
                pluralStringResource(R.plurals.sos_family_told, state.alerted, state.alerted)
            } else {
                stringResource(R.string.sos_family_waiting)
            }
        StepRow(Icons.Filled.Groups, family)
        CallStep(state)
        StepRow(Icons.Filled.LocationOn, locationText(state))
        TextsStep(state, callbacks.onSendTexts)
    }
    val helper = state.settings.helperName.ifBlank { stringResource(R.string.sos_helper) }
    if (state.settings.hasHelper) {
        IconWideButton(Icons.Filled.Call, stringResource(R.string.sos_call_helper, helper), callbacks.onCallHelper)
    } else {
        StepRow(Icons.Filled.Call, stringResource(R.string.sos_no_helper))
    }
    SosWideButton(
        stringResource(R.string.sos_call_emergency, state.settings.emergencyNumber),
        callbacks.onCallEmergency,
    )
    IconWideButton(
        Icons.Filled.VolumeUp,
        stringResource(if (state.alarmOn) R.string.sos_alarm_stop else R.string.sos_alarm_start),
    ) { callbacks.onAlarm(!state.alarmOn) }
    WideButton(stringResource(R.string.sos_safe), callbacks.onSafe, primary = true)
}

@Composable
private fun CallStep(state: SosState) {
    val helper = state.settings.helperName.ifBlank { stringResource(R.string.sos_helper) }
    when (state.helperAnswered) {
        true -> StepRow(Icons.Filled.CheckCircle, stringResource(R.string.sos_helper_answered, helper))
        false -> StepRow(Icons.Filled.Call, stringResource(R.string.sos_helper_no_answer, helper))
        null -> if (state.helperCalled) StepRow(Icons.Filled.Call, stringResource(R.string.sos_calling_helper, helper))
    }
    if (state.emergencyCalled) {
        StepRow(
            Icons.Filled.Call,
            stringResource(R.string.sos_emergency_called, state.settings.emergencyNumber),
            strong = true,
        )
    }
}

@Composable
private fun locationText(state: SosState): String =
    when {
        !state.settings.sendLocation -> stringResource(R.string.sos_location_off)
        state.locating -> stringResource(R.string.sos_locating)
        state.location != null -> stringResource(R.string.sos_location_found)
        else -> stringResource(R.string.sos_location_unknown)
    }

@Composable
private fun TextsStep(
    state: SosState,
    onSendTexts: () -> Unit,
) {
    when {
        state.textsSent > 0 ->
            StepRow(Icons.Filled.Sms, pluralStringResource(R.plurals.sos_texts_sent, state.textsSent, state.textsSent))
        state.textsNeedTap -> IconWideButton(Icons.Filled.Sms, stringResource(R.string.sos_send_texts), onSendTexts)
        state.settings.textNumbers.isEmpty() -> Unit
        else -> StepRow(Icons.Filled.Sms, stringResource(R.string.sos_texts_waiting))
    }
}

@Composable
private fun StepRow(
    icon: ImageVector,
    text: String,
    strong: Boolean = false,
) {
    val c = LocalPatientColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (strong) c.help else c.ink, modifier = Modifier.size(32.dp))
        Text(
            text,
            style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = c.ink,
        )
    }
}

@Composable
private fun SosWideButton(
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Surface(
        color = c.sos,
        contentColor = c.sosInk,
        shape = RoundedCornerShape(22.dp),
        border = if (c.outlined) BorderStroke(3.dp, c.ink) else null,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .selectable(selected = false, role = Role.Button, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun IconWideButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(c.surface, RoundedCornerShape(22.dp))
            .border(2.dp, c.hairline, RoundedCornerShape(22.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = c.ink)
    }
}
