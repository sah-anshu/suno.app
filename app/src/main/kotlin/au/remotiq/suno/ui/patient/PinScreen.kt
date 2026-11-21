// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.remotiq.suno.R
import au.remotiq.suno.patient.PinCode

/**
 * Large 3×4 keypad. In check mode [onComplete] returns whether the PIN was accepted; a wrong PIN
 * clears the dots and shows a message. In set mode any four digits are accepted.
 */
@Composable
fun PinScreen(
    setMode: Boolean,
    onComplete: (String) -> Boolean,
    onBack: (() -> Unit)?,
) {
    val c = LocalPatientColors.current
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }

    fun press(digit: Char) {
        if (pin.length >= PinCode.LENGTH) return
        pin += digit
        wrong = false
        if (pin.length == PinCode.LENGTH) {
            val ok = onComplete(pin)
            if (!ok) wrong = true
            pin = ""
        }
    }

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                if (onBack !=
                    null
                ) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.pin_back), onBack)
                }
            }
            Box(Modifier.size(84.dp).background(c.surfaceSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, null, tint = c.ink, modifier = Modifier.size(40.dp))
            }
            Text(
                stringResource(if (setMode) R.string.pin_set_title else R.string.pin_title),
                style = MaterialTheme.typography.headlineMedium,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    when {
                        wrong -> R.string.pin_wrong
                        setMode -> R.string.pin_set_hint
                        else -> R.string.pin_hint
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (wrong) c.ink else c.muted,
                textAlign = TextAlign.Center,
            )
            val progress = stringResource(R.string.pin_progress, pin.length)
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            progress
                    },
            ) {
                repeat(PinCode.LENGTH) { i ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(
                                if (i <
                                    pin.length
                                ) {
                                    c.ink
                                } else {
                                    c.background
                                },
                                CircleShape,
                            ).border(3.dp, c.ink, CircleShape),
                    )
                }
            }
            PinKeypad(onDigit = ::press, onDelete = { pin = pin.dropLast(1) })
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalPatientColors.current
    Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d -> Key(d.toString(), Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.weight(1f))
            Key("0", Modifier.weight(1f)) { onDigit('0') }
            val delete = stringResource(R.string.pin_delete)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 76.dp)
                    .selectable(selected = false, role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = delete },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = c.ink, modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPatientColors.current
    Box(
        modifier
            .heightIn(min = 76.dp)
            .background(c.surface, CircleShape)
            .border(2.dp, c.hairline, CircleShape)
            .selectable(selected = false, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = c.ink)
    }
}
