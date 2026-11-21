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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.remotiq.suno.R
import au.remotiq.suno.data.Favourite

/** People: one large tile per person; one tap calls. The helper chooses who is here. */
@Composable
fun PeopleScreen(
    people: List<Favourite>,
    onCall: (Favourite) -> Unit,
    onKeypad: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalPatientColors.current
    PatientPage {
        Text(stringResource(R.string.people_title), style = MaterialTheme.typography.headlineMedium, color = c.ink)
        if (people.isEmpty()) {
            PatientCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.people_empty), style = MaterialTheme.typography.bodyLarge, color = c.ink)
            }
        }
        people.forEach { person -> PersonTile(person) { onCall(person) } }
        IconRowButton(Icons.Filled.Dialpad, stringResource(R.string.people_keypad), onKeypad)
        WideButton(stringResource(R.string.people_back), onBack)
    }
}

@Composable
private fun PersonTile(
    person: Favourite,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    val label = stringResource(R.string.people_call, person.name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(c.surface, RoundedCornerShape(24.dp))
            .border(if (c.outlined) 2.dp else 1.dp, c.hairline, RoundedCornerShape(24.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(64.dp).background(c.on, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                person.name
                    .trim()
                    .take(1)
                    .uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = c.onInk,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleLarge, color = c.ink)
            if (person.relationship.isNotBlank()) {
                Text(person.relationship, style = MaterialTheme.typography.bodyLarge, color = c.muted)
            }
        }
        Box(Modifier.size(56.dp).background(c.help, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Call, null, tint = c.helpInk, modifier = Modifier.size(30.dp))
        }
    }
}

/** A large keypad for numbers that are not in People. */
@Composable
fun KeypadScreen(
    number: String,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalPatientColors.current
    PatientPage {
        Text(
            number.ifEmpty { stringResource(R.string.keypad_hint) },
            style = MaterialTheme.typography.headlineLarge,
            color = if (number.isEmpty()) c.muted else c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key -> KeyButton(key, Modifier.weight(1f)) { onDigit(key) } }
            }
        }
        IconRowButton(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.keypad_delete), onDelete)
        if (number.isNotEmpty()) {
            BigCallButton(Icons.Filled.Call, stringResource(R.string.keypad_call), c.help, c.helpInk, onCall)
        }
        WideButton(stringResource(R.string.people_back), onBack)
    }
}

@Composable
private fun KeyButton(
    key: Char,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Box(
        modifier
            .heightIn(min = 76.dp)
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(2.dp, c.hairline, RoundedCornerShape(20.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(key.toString(), style = MaterialTheme.typography.headlineMedium, color = c.ink)
    }
}

@Composable
internal fun IconRowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            .selectable(selected = false, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = c.ink)
    }
}

@Composable
private fun PatientPage(content: @Composable () -> Unit) {
    val c = LocalPatientColors.current
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}
