// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.remotiq.suno.R
import au.remotiq.suno.data.SosSettings
import au.remotiq.suno.ui.common.SectionGroup
import au.remotiq.suno.ui.common.SurfaceCard

/** Permissions SOS uses: calling the helper, sending texts, and the phone's GPS place. */
private val SOS_PERMISSIONS =
    arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

private fun missingSosPermissions(context: Context): List<String> =
    SOS_PERMISSIONS.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

/** Helper mode → Settings → SOS: who is called and texted, and what SOS does. */
@Composable
fun SosHelperSettings(viewModel: SosHelperModel = hiltViewModel()) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()
    val current = saved ?: return
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(current.helperName) }
    var number by rememberSaveable { mutableStateOf(current.helperNumber) }
    var emergency by rememberSaveable { mutableStateOf(current.emergencyNumber) }
    var texts by rememberSaveable { mutableStateOf(current.textNumbers.joinToString(", ")) }
    var permissionCheck by rememberSaveable { mutableIntStateOf(0) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionCheck++ }
    var missing by rememberSaveable { mutableStateOf(listOf<String>()) }
    LaunchedEffect(permissionCheck) { missing = missingSosPermissions(context) }

    SectionGroup(stringResource(R.string.sos_settings_title)) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(name, { name = it }, R.string.sos_settings_helper_name, KeyboardType.Text)
                Field(number, { number = it }, R.string.sos_settings_helper_number, KeyboardType.Phone)
                Field(emergency, { emergency = it }, R.string.sos_settings_emergency, KeyboardType.Phone)
                OutlinedTextField(
                    value = texts,
                    onValueChange = { texts = it },
                    label = { Text(stringResource(R.string.sos_settings_texts)) },
                    supportingText = { Text(stringResource(R.string.sos_settings_texts_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Toggle(R.string.sos_settings_location, current.sendLocation) {
                    viewModel.save(current.copy(sendLocation = it))
                }
                Toggle(R.string.sos_settings_alarm, current.alarmOnStart) {
                    viewModel.save(current.copy(alarmOnStart = it))
                }
                Button(
                    onClick = {
                        viewModel.save(
                            current.copy(
                                helperName = name,
                                helperNumber = number,
                                emergencyNumber = emergency,
                                textNumbers = SosSettings.parseNumbers(texts),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sos_settings_save)) }
                Text(
                    stringResource(
                        if (missing.isEmpty()) R.string.sos_settings_allowed else R.string.sos_settings_not_allowed,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (missing.isNotEmpty()) {
                    TextButton(
                        onClick = { launcher.launch(missing.toTypedArray()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.sos_settings_allow)) }
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    type: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
