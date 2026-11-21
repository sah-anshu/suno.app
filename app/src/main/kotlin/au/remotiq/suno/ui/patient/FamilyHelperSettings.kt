// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.remotiq.suno.R
import au.remotiq.suno.data.PreferenceStorage
import au.remotiq.suno.data.StoredCompanion
import au.remotiq.suno.family.HostStatus
import au.remotiq.suno.family.PatientLinkHost
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.ui.common.SectionGroup
import au.remotiq.suno.ui.common.SurfaceCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Helper mode → Settings: who can talk to the patient, and the join code. */
@Composable
fun FamilyHelperSettings(viewModel: FamilyHelperModel = hiltViewModel()) {
    val approved by viewModel.approved.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var showCode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { showCode = true }

    if (showCode) JoinCodeDialog(viewModel) { showCode = false }

    SectionGroup(stringResource(R.string.family_settings_title)) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.family_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    statusText(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        val missing = LinkPermissions.missing(context, LinkPermissions.host())
                        if (missing.isEmpty()) showCode = true else permissions.launch(missing.toTypedArray())
                    },
                    enabled = LinkPermissions.supported(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.family_show_code)) }
                if (approved.isEmpty()) {
                    Text(stringResource(R.string.family_none), style = MaterialTheme.typography.bodyMedium)
                }
                approved.forEach { companion ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            companion.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { viewModel.remove(companion.id) },
                        ) { Text(stringResource(R.string.family_remove)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(status: HostStatus): String =
    when {
        status.unavailable -> stringResource(R.string.family_status_unavailable)
        status.running ->
            stringResource(
                R.string.family_status_ready,
                listOfNotNull(
                    stringResource(R.string.family_wifi).takeIf { status.wifiDirect },
                    stringResource(R.string.family_bluetooth).takeIf { status.bluetooth },
                ).joinToString(", "),
            )
        else -> stringResource(R.string.family_status_off)
    }

@Composable
private fun JoinCodeDialog(
    viewModel: FamilyHelperModel,
    onClose: () -> Unit,
) {
    // The link runs while the code is on screen, so a companion can join right away.
    DisposableEffect(Unit) {
        viewModel.hold(true)
        onDispose { viewModel.hold(false) }
    }
    val uri by produceState<String?>(null) { value = viewModel.joinUri() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.family_code_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                uri?.let {
                    QrCode(
                        it,
                        stringResource(R.string.family_code_title),
                        Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.family_code_body), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.family_close)) } },
    )
}
