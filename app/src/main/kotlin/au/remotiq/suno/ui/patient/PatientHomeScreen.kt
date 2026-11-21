// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import au.remotiq.suno.R
import au.remotiq.suno.audiograph.SourceMode
import au.remotiq.suno.data.Favourite
import au.remotiq.suno.data.PatientSettings
import au.remotiq.suno.patient.Place

/**
 * The patient's Home: one job per band, large targets, and every state shown with an icon, a word
 * and a shape as well as colour (arch.md section 11).
 */
@Composable
fun PatientHomeScreen(
    state: PatientUiState,
    device: DeviceStatus,
    placeSheetOpen: Boolean,
    onListen: () -> Unit,
    onVolume: (Int) -> Unit,
    onOpenPlaces: () -> Unit,
    onClosePlaces: () -> Unit,
    onPlace: (Place) -> Unit,
    onHelper: () -> Unit,
    message: String?,
    onChooseSource: (SourceMode) -> Unit = {},
    onSos: () -> Unit = {},
    quickCall: Favourite? = null,
    onQuickCall: () -> Unit = {},
    onPeople: () -> Unit = {},
) {
    val c = LocalPatientColors.current
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SosButton(onSos)
                RoundIconButton(Icons.Filled.Settings, stringResource(R.string.patient_helper), onHelper)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.patient_hello),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.ink,
                )
                StatusLine(device)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                ListenButton(state, onListen)
            }
            VolumeRow(state.volumeStep, state.volumeCap, onVolume)
            if (message != null) MessageCard(message)
            state.talkers.firstOrNull()?.let { TalkingBanner(it, listening = state.running) }
            if (state.settings.homeLevel >= 2 && state.companions.isNotEmpty()) {
                NowHearingCard(state.people, state.hasTv, state.remote, state.sourceMode, onChooseSource)
            }
            when (state.settings.homeLevel) {
                2 -> PlaceRow(state.place, state.settings.picturesMode, onOpenPlaces)
                PatientSettings.HOME_LEVELS -> PlaceGrid(state.place, state.settings.picturesMode, onPlace)
                else -> Unit
            }
            CallDock(quickCall, onQuickCall, onPeople)
            Spacer(Modifier.heightIn(min = 8.dp))
        }
    }
    if (placeSheetOpen) PlaceDialog(state.place, onPlace, onClosePlaces)
}

/** Bottom dock: call the first person in People (or the helper) in one tap, or open People. */
@Composable
private fun CallDock(
    quickCall: Favourite?,
    onQuickCall: () -> Unit,
    onPeople: () -> Unit,
) {
    val c = LocalPatientColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (quickCall != null) {
            DockButton(
                Icons.Filled.Call,
                stringResource(R.string.people_call, quickCall.name),
                c.help,
                c.helpInk,
                Modifier.weight(1f),
                onQuickCall,
            )
        }
        DockButton(
            Icons.Filled.Contacts,
            stringResource(R.string.people_title),
            c.surface,
            c.ink,
            Modifier.weight(1f),
            onPeople,
        )
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    label: String,
    color: Color,
    ink: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Row(
        modifier
            .heightIn(min = 80.dp)
            .background(color, RoundedCornerShape(22.dp))
            .border(2.dp, if (color == c.surface) c.hairline else color, RoundedCornerShape(22.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(30.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 2)
    }
}

@Composable
private fun StatusLine(device: DeviceStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusItem(
            Icons.Filled.Headphones,
            stringResource(if (device.earphones) R.string.patient_earphones_ok else R.string.patient_earphones_missing),
        )
        device.batteryPercent?.let { StatusItem(Icons.Filled.BatteryStd, "$it%") }
        StatusItem(Icons.Filled.Check, stringResource(R.string.patient_profile_on))
    }
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    label: String,
) {
    val c = LocalPatientColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

@Composable
private fun ListenButton(
    state: PatientUiState,
    onListen: () -> Unit,
) {
    val c = LocalPatientColors.current
    val haptics = LocalHapticFeedback.current
    val on = state.running
    val title =
        stringResource(
            when {
                on -> R.string.patient_listening
                state.connecting -> R.string.patient_connecting
                else -> R.string.patient_off
            },
        )
    val hint = stringResource(if (on || state.connecting) R.string.patient_tap_stop else R.string.patient_tap_start)
    Box(
        Modifier
            .sizeIn(maxWidth = 280.dp)
            .fillMaxWidth(0.7f)
            .aspectRatio(1f)
            .drawBehind {
                // Halo rings only while listening: the shape changes, not just the colour.
                if (on) {
                    val r = size.minDimension / 2
                    drawCircle(c.onSofter, radius = r * HALO_OUTER)
                    drawCircle(c.onSoft, radius = r * HALO_INNER)
                }
            }.background(if (on) c.on else c.surface, CircleShape)
            .drawWithContent {
                drawContent()
                // Off: a dashed edge drawn on top of the fill, so the button stays visible.
                if (!on) {
                    val stroke = 4.dp.toPx()
                    drawCircle(
                        c.hairline,
                        radius = size.minDimension / 2 - stroke / 2,
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                    )
                }
            }.selectable(selected = on, role = Role.Switch) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onListen()
            }.semantics {
                contentDescription = "$title. $hint"
                stateDescription = title
            }.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                if (on) Icons.Filled.GraphicEq else Icons.Filled.HearingDisabled,
                contentDescription = null,
                tint = if (on) c.onInk else c.muted,
                modifier = Modifier.size(56.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = if (on) c.onInk else c.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) c.onInk else c.muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private const val HALO_OUTER = 1.26f
private const val HALO_INNER = 1.13f

@Composable
private fun VolumeRow(
    step: Int,
    cap: Int,
    onVolume: (Int) -> Unit,
) {
    val c = LocalPatientColors.current
    val label = stringResource(R.string.patient_volume, step, PatientSettings.VOLUME_STEPS)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            Icons.Filled.Remove,
            stringResource(R.string.patient_softer),
            { onVolume(-1) },
            enabled =
                step > 1,
            size = 76,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PatientSettings.VOLUME_STEPS) { i ->
                    val filled = i < step
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(if (filled) c.ink else c.background, CircleShape)
                            .border(2.dp, if (i < cap) c.ink else c.hairline, CircleShape),
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        RoundIconButton(Icons.Filled.Add, stringResource(R.string.patient_louder), {
            onVolume(1)
        }, enabled = step < cap, size = 76)
    }
}

@Composable
private fun MessageCard(text: String) {
    val c = LocalPatientColors.current
    Surface(
        color = c.warn,
        contentColor = c.warnInk,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(18.dp))
    }
}

private val Place.icon: ImageVector
    get() =
        when (this) {
            Place.HOME -> Icons.Filled.Home
            Place.PARK -> Icons.Filled.Park
            Place.TRAFFIC -> Icons.Filled.DirectionsCar
            Place.NIGHT -> Icons.Filled.Bedtime
        }

private val Place.label: Int
    get() =
        when (this) {
            Place.HOME -> R.string.place_home
            Place.PARK -> R.string.place_park
            Place.TRAFFIC -> R.string.place_traffic
            Place.NIGHT -> R.string.place_night
        }

private val Place.detail: Int
    get() =
        when (this) {
            Place.HOME -> R.string.place_home_detail
            Place.PARK -> R.string.place_park_detail
            Place.TRAFFIC -> R.string.place_traffic_detail
            Place.NIGHT -> R.string.place_night_detail
        }

@Composable
private fun PlaceRow(
    place: Place,
    pictures: Boolean,
    onOpen: () -> Unit,
) {
    val c = LocalPatientColors.current
    PatientCard(Modifier.fillMaxWidth().selectable(selected = false, role = Role.Button, onClick = onOpen)) {
        Text(stringResource(R.string.patient_place), style = MaterialTheme.typography.labelMedium, color = c.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).background(c.onSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(place.icon, null, tint = c.ink, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(place.label), style = MaterialTheme.typography.titleLarge, color = c.ink)
                if (!pictures) {
                    Text(
                        stringResource(place.detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = c.muted)
        }
    }
}

@Composable
private fun PlaceGrid(
    selected: Place,
    pictures: Boolean,
    onPlace: (Place) -> Unit,
) {
    val c = LocalPatientColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.patient_place), style = MaterialTheme.typography.labelMedium, color = c.muted)
        Place.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { place ->
                    PlaceTile(place, place == selected, pictures, { onPlace(place) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaceTile(
    place: Place,
    selected: Boolean,
    pictures: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPatientColors.current
    val selectedText = stringResource(R.string.patient_selected)
    val bg = if (selected) c.on else c.surface
    val fg = if (selected) c.onInk else c.ink
    Box(
        modifier
            .heightIn(min = 104.dp)
            .background(bg, RoundedCornerShape(20.dp))
            .border(if (selected) 4.dp else 2.dp, if (selected) c.on else c.hairline, RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).size(26.dp).background(c.onInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = c.on, modifier = Modifier.size(18.dp))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(place.icon, null, tint = fg, modifier = Modifier.size(36.dp))
            Text(stringResource(place.label), style = MaterialTheme.typography.titleMedium, color = fg)
            if (!pictures) {
                Text(
                    stringResource(place.detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PlaceDialog(
    selected: Place,
    onPlace: (Place) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalPatientColors.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = c.background,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(16.dp).widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.patient_place),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.ink,
                )
                Place.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { place ->
                            PlaceTile(place, place == selected, false, { onPlace(place) }, Modifier.weight(1f))
                        }
                    }
                }
                WideButton(stringResource(R.string.patient_close), onClose)
            }
        }
    }
}

@Composable
internal fun PatientCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = LocalPatientColors.current
    Surface(
        modifier = modifier,
        color = c.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(if (c.outlined) 2.dp else 1.dp, c.hairline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
internal fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Int = 60,
) {
    val c = LocalPatientColors.current
    Box(
        Modifier
            .size(size.dp)
            .background(c.surface, CircleShape)
            .border(2.dp, if (enabled) c.hairline else c.surfaceSoft, CircleShape)
            .selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) c.ink else c.hairline,
            modifier =
                Modifier.size(
                    (
                        size *
                            0.46f
                    ).dp,
                ),
        )
    }
}

@Composable
internal fun WideButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val c = LocalPatientColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (primary) c.on else c.surface, RoundedCornerShape(22.dp))
            .border(2.dp, if (primary) c.on else c.hairline, RoundedCornerShape(22.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (primary) c.onInk else c.ink)
    }
}
