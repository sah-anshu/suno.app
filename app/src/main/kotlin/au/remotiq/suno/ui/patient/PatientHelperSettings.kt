// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.remotiq.suno.R
import au.remotiq.suno.data.PatientSettings
import au.remotiq.suno.patient.PinCode
import au.remotiq.suno.ui.common.AdaptiveChoices
import au.remotiq.suno.ui.common.SectionGroup
import au.remotiq.suno.ui.common.SurfaceCard

/** Helper-only settings for the patient's screen, shown in Helper mode → Settings. */
@Composable
fun PatientHelperSettings(viewModel: PatientStateModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state.settings
    var changingPin by rememberSaveable { mutableStateOf(false) }
    if (changingPin) {
        ChangePinDialog(onSave = {
            viewModel.setPin(it)
            changingPin = false
        }, onDismiss = { changingPin = false })
    }
    SectionGroup(stringResource(R.string.settings_patient)) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(stringResource(R.string.settings_home_level), stringResource(R.string.settings_home_level_detail))
                AdaptiveChoices(
                    listOf(
                        R.string.home_level_1,
                        R.string.home_level_2,
                        R.string.home_level_3,
                    ).map { stringResource(it) },
                    s.homeLevel - 1,
                    { i -> viewModel.updateSettings { it.copy(homeLevel = i + 1) } },
                )
                Label(stringResource(R.string.settings_home_style))
                AdaptiveChoices(
                    listOf(stringResource(R.string.home_style_pictures), stringResource(R.string.home_style_words)),
                    if (s.picturesMode) 0 else 1,
                    { i -> viewModel.updateSettings { it.copy(picturesMode = i == 0) } },
                )
                Label(stringResource(R.string.settings_colours))
                val themes = PatientThemeName.entries
                AdaptiveChoices(
                    listOf(
                        R.string.theme_standard,
                        R.string.theme_dark,
                        R.string.theme_yellow,
                    ).map { stringResource(it) },
                    themes.indexOf(PatientThemeName.fromName(s.theme)),
                    { i -> viewModel.updateSettings { it.copy(theme = themes[i].name) } },
                )
                Label(stringResource(R.string.settings_max_volume))
                AdaptiveChoices(
                    (1..PatientSettings.VOLUME_STEPS).map { it.toString() },
                    s.maxVolumeStep - 1,
                    { i -> viewModel.updateSettings { it.copy(maxVolumeStep = i + 1) } },
                )
                val autoLabel = stringResource(R.string.settings_auto_start)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f),
                    ) { Label(autoLabel, stringResource(R.string.settings_auto_start_detail)) }
                    Switch(
                        s.autoStart,
                        { on -> viewModel.updateSettings { it.copy(autoStart = on) } },
                        modifier = Modifier.semantics { contentDescription = autoLabel },
                    )
                }
                TextButton(onClick = { changingPin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_change_pin))
                }
            }
        }
    }
}

@Composable
private fun Label(
    title: String,
    detail: String? = null,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (detail != null) {
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChangePinDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pin_set_hint))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter(Char::isDigit).take(PinCode.LENGTH) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pin) },
                enabled = PinCode.isValid(pin),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
