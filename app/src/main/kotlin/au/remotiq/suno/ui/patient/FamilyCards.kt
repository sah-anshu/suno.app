// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.remotiq.suno.R
import au.remotiq.suno.audiograph.SourceMode
import au.remotiq.suno.audiograph.StreamKind
import au.remotiq.suno.audiograph.Talker
import au.remotiq.suno.link.CompanionDevice
import au.remotiq.suno.link.DeviceKinds

/** Full screen over Home when a new phone asks to join. Two large answers, no timeout. */
@Composable
fun JoinApprovalScreen(
    device: CompanionDevice,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Initial(device.deviceName, 120)
            Text(
                stringResource(
                    if (device.deviceKind ==
                        DeviceKinds.TV
                    ) {
                        R.string.family_wants_to_share
                    } else {
                        R.string.family_wants_to_talk
                    },
                    device.deviceName,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    if (device.deviceKind ==
                        DeviceKinds.TV
                    ) {
                        R.string.family_share_body
                    } else {
                        R.string.family_wants_body
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = c.muted,
                textAlign = TextAlign.Center,
            )
            WideButton(stringResource(R.string.family_allow), onAllow, primary = true)
            WideButton(stringResource(R.string.family_deny), onDeny)
        }
    }
}

/** Shown on every Home level while someone talks, so the patient knows who it is. */
@Composable
fun TalkingBanner(
    talker: Talker,
    listening: Boolean,
) {
    val c = LocalPatientColors.current
    Surface(
        color = c.ink,
        contentColor = c.background,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.RecordVoiceOver, null, modifier = Modifier.size(36.dp))
            Column {
                Text(
                    stringResource(R.string.family_is_talking, talker.name),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (!listening) {
                    Text(
                        stringResource(R.string.family_tap_listen),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * What the patient is hearing, and the choice between the room, the TV and family members. Only
 * connected sources appear. Tapping the chosen source again returns to Auto (person, then TV, then room).
 */
@Composable
fun NowHearingCard(
    people: List<CompanionDevice>,
    hasTv: Boolean,
    remote: List<Talker>,
    mode: SourceMode,
    onChoose: (SourceMode) -> Unit,
) {
    val c = LocalPatientColors.current
    val hearing = nowHearing(people, remote, mode)
    val current =
        when (hearing.source) {
            HeardSource.PERSON -> hearing.personName
            HeardSource.TV -> stringResource(R.string.family_tv)
            HeardSource.ROOM -> stringResource(R.string.family_room)
        }
    val icon =
        when (hearing.source) {
            HeardSource.PERSON -> Icons.Filled.Person
            HeardSource.TV -> Icons.Filled.Tv
            HeardSource.ROOM -> Icons.Filled.Hearing
        }
    PatientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).background(c.onSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.ink, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.family_now_hearing),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.muted,
                )
                Text(current, style = MaterialTheme.typography.titleLarge, color = c.ink)
            }
            if (mode == SourceMode.AUTO) {
                Text(
                    stringResource(R.string.family_auto),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink,
                    modifier =
                        Modifier
                            .border(2.dp, c.ink, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        val personLabel = people.joinToString { it.deviceName }
        val chips =
            buildList {
                add(Triple(SourceMode.ROOM_ONLY, Icons.Filled.Hearing, stringResource(R.string.family_room)))
                if (hasTv) add(Triple(SourceMode.TV_ONLY, Icons.Filled.Tv, stringResource(R.string.family_tv)))
                if (people.isNotEmpty()) add(Triple(SourceMode.PERSON_ONLY, Icons.Filled.Person, personLabel))
            }
        // Two per row, so labels stay whole at large text sizes.
        chips.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (chipMode, chipIcon, label) ->
                    SourceChip(chipIcon, label, mode == chipMode, Modifier.weight(1f)) { onChoose(chipMode) }
                }
            }
        }
    }
}

internal enum class HeardSource { ROOM, TV, PERSON }

internal data class Heard(
    val source: HeardSource,
    val personName: String = "",
)

/** What the patient hears now, following the mixer's priorities (person, then TV, then room). */
internal fun nowHearing(
    people: List<CompanionDevice>,
    remote: List<Talker>,
    mode: SourceMode,
): Heard {
    val person = remote.firstOrNull { it.kind == StreamKind.VOICE }?.takeIf { mode != SourceMode.ROOM_ONLY }
    val tvPlaying = remote.any { it.kind == StreamKind.MEDIA }
    return when {
        person != null -> Heard(HeardSource.PERSON, person.name)
        mode == SourceMode.PERSON_ONLY -> Heard(HeardSource.PERSON, people.joinToString { it.deviceName })
        mode == SourceMode.TV_ONLY || (mode == SourceMode.AUTO && tvPlaying) -> Heard(HeardSource.TV)
        else -> Heard(HeardSource.ROOM)
    }
}

@Composable
private fun SourceChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    val selectedText = stringResource(R.string.patient_selected)
    Row(
        modifier
            .heightIn(min = 64.dp)
            .background(if (selected) c.on else c.background, RoundedCornerShape(18.dp))
            .border(2.dp, if (selected) c.on else c.hairline, RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            if (selected) Icons.Filled.Check else icon,
            null,
            tint = if (selected) c.onInk else c.ink,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) c.onInk else c.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun Initial(
    name: String,
    size: Int,
) {
    val c = LocalPatientColors.current
    Box(Modifier.size(size.dp).background(c.on, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = c.onInk)
    }
}
