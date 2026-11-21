// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.remotiq.suno.R
import au.remotiq.suno.data.CallSettings
import au.remotiq.suno.data.Favourite
import au.remotiq.suno.ui.common.SectionGroup
import au.remotiq.suno.ui.common.SurfaceCard

/** Helper mode → Settings: make Suno the phone app, choose People, and how calls are handled. */
@Composable
fun CallHelperSettings(viewModel: CallHelperModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { uri ->
                readPicked(context, uri)?.let { (name, number) ->
                    draft =
                        listOf(name, "", number)
                }
            }
        }

    draft?.let { values ->
        PersonDialog(values, onSave = {
            viewModel.add(it)
            draft = null
        }, onCancel = { draft = null })
    }

    SectionGroup(stringResource(R.string.calls_settings_title)) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhoneAppRow(isPhoneApp = refresh >= 0 && viewModel.isPhoneApp()) { role.launch(roleIntent(context)) }
                PeopleList(settings.favourites, viewModel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            picker.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                            )
                        },
                        enabled = settings.favourites.size < CallSettings.MAX_FAVOURITES,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.calls_add_contact)) }
                    TextButton(
                        onClick = { draft = listOf("", "", "") },
                        enabled = settings.favourites.size < CallSettings.MAX_FAVOURITES,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.calls_add_typed)) }
                }
                SettingSwitch(R.string.calls_contacts_only, settings.contactsOnly) { on ->
                    viewModel.set { it.copy(contactsOnly = on) }
                }
                SettingSwitch(R.string.calls_auto_answer, settings.autoAnswer) { on ->
                    viewModel.set { it.copy(autoAnswer = on) }
                }
                SettingSwitch(R.string.calls_sos_emergency, settings.emergencyIfNoAnswer) { on ->
                    viewModel.set { it.copy(emergencyIfNoAnswer = on) }
                }
                TextButton(
                    onClick = {
                        permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.calls_allow_contacts)) }
            }
        }
    }
}

@Composable
private fun PhoneAppRow(
    isPhoneApp: Boolean,
    onMake: () -> Unit,
) {
    Text(
        stringResource(if (isPhoneApp) R.string.calls_is_phone_app else R.string.calls_not_phone_app),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!isPhoneApp) {
        Button(
            onClick = onMake,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.calls_make_phone_app)) }
    }
}

@Composable
private fun PeopleList(
    people: List<Favourite>,
    viewModel: CallHelperModel,
) {
    if (people.isEmpty()) Text(stringResource(R.string.calls_no_people), style = MaterialTheme.typography.bodyMedium)
    people.forEachIndexed { i, person ->
        Column {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(person.relationship, person.number).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(
                    onClick = { viewModel.move(i, -1) },
                    enabled = i > 0,
                ) { Text(stringResource(R.string.calls_up)) }
                TextButton(onClick = { viewModel.move(i, 1) }, enabled = i < people.lastIndex) {
                    Text(stringResource(R.string.calls_down))
                }
                TextButton(onClick = { viewModel.remove(i) }) { Text(stringResource(R.string.family_remove)) }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PersonDialog(
    initial: List<String>,
    onSave: (Favourite) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial[0]) }
    var relationship by rememberSaveable { mutableStateOf(initial[1]) }
    var number by rememberSaveable { mutableStateOf(initial[2]) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.calls_person_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.calls_person_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    relationship,
                    { relationship = it },
                    label = { Text(stringResource(R.string.calls_person_relationship)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    number,
                    { number = it },
                    label = { Text(stringResource(R.string.calls_person_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Favourite(name, relationship, number)) },
                enabled = name.isNotBlank() && number.any { it.isDigit() },
            ) { Text(stringResource(R.string.calls_person_save)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.patient_close)) } },
    )
}

private fun roleIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else {
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }

/** Name and number from the contact picker (the picker grants access to this one entry only). */
private fun readPicked(
    context: Context,
    uri: Uri,
): Pair<String, String>? =
    runCatching {
        context.contentResolver
            .query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0).orEmpty() to it.getString(1).orEmpty() else null }
    }.getOrNull()
