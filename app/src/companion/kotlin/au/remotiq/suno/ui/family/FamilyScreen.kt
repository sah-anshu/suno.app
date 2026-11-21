// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.family

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import au.remotiq.suno.family.CompanionLink
import au.remotiq.suno.family.CompanionStatus
import au.remotiq.suno.family.TalkService
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.ui.common.ActionButton
import au.remotiq.suno.ui.common.ActionStyle
import au.remotiq.suno.ui.common.EarPage
import au.remotiq.suno.ui.common.PageHeading
import au.remotiq.suno.ui.common.SectionGroup
import au.remotiq.suno.ui.common.SurfaceCard
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyModel
    @Inject
    constructor(
        private val link: CompanionLink,
        private val preferences: PreferenceStorage,
    ) : ViewModel() {
        val status: StateFlow<CompanionStatus> = link.status
        val myName: StateFlow<String> =
            preferences
                .observePatientSettings()
                .map {
                    it.displayName
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

        fun setMyName(value: String) {
            viewModelScope.launch {
                preferences.setPatientSettings(preferences.observePatientSettings().first().copy(displayName = value))
            }
        }

        fun join(scanned: String) = link.join(scanned)

        fun reconnect() = link.reconnect()

        fun leave() = link.leave()
    }

/** Companion app, first tab: join the patient's Suno and talk to them. */
@Composable
fun FamilyScreen(viewModel: FamilyModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val myName by viewModel.myName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scan = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(viewModel::join) }
    val joinPermissions =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { scan.launch(scanOptions()) }
    val talkPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.RECORD_AUDIO] == true) TalkService.start(context)
        }

    EarPage {
        PageHeading(stringResource(R.string.nav_family), stringResource(R.string.family_subtitle))
        when (status.state) {
            ClientState.IDLE ->
                JoinCard(myName, viewModel::setMyName) {
                    val missing = LinkPermissions.missing(context, LinkPermissions.companion())
                    if (missing.isEmpty()) {
                        scan.launch(
                            scanOptions(),
                        )
                    } else {
                        joinPermissions.launch(missing.toTypedArray())
                    }
                }
            ClientState.CONNECTING, ClientState.WAITING_FOR_APPROVAL ->
                StatusCard(
                    title =
                        stringResource(
                            if (status.state ==
                                ClientState.CONNECTING
                            ) {
                                R.string.family_connecting
                            } else {
                                R.string.family_waiting
                            },
                            status.patientName.orEmpty(),
                        ),
                    action = stringResource(R.string.family_cancel),
                    onAction = viewModel::leave,
                )
            ClientState.CONNECTED ->
                ConnectedCard(status, onTalk = {
                    if (status.talking) {
                        TalkService.stop(context)
                    } else {
                        talkPermissions.launch(talkPermissionList())
                    }
                }, onLeave = {
                    TalkService.stop(context)
                    viewModel.leave()
                })
            ClientState.DENIED ->
                StatusCard(
                    stringResource(R.string.family_denied),
                    stringResource(R.string.family_scan_again),
                    viewModel::leave,
                )
            ClientState.CLOSED ->
                StatusCard(
                    stringResource(R.string.family_not_connected, status.patientName.orEmpty()),
                    stringResource(R.string.family_reconnect),
                    viewModel::reconnect,
                    secondary = stringResource(R.string.family_forget) to viewModel::leave,
                )
        }
        SectionGroup(stringResource(R.string.family_how_title)) {
            Text(stringResource(R.string.family_how_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun JoinCard(
    myName: String,
    onName: (String) -> Unit,
    onScan: () -> Unit,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.family_join_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.family_join_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = myName,
                onValueChange = onName,
                label = { Text(stringResource(R.string.family_my_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                stringResource(R.string.family_scan),
                onScan,
                modifier = Modifier.fillMaxWidth(),
                enabled = LinkPermissions.supported(),
                icon = Icons.Filled.QrCodeScanner,
            )
            if (!LinkPermissions.supported()) {
                Text(
                    stringResource(R.string.family_needs_android10),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ConnectedCard(
    status: CompanionStatus,
    onTalk: () -> Unit,
    onLeave: () -> Unit,
) {
    val via =
        stringResource(
            if (status.transport ==
                CompanionStatus.Transport.BLUETOOTH
            ) {
                R.string.family_via_bluetooth
            } else {
                R.string.family_via_wifi
            },
        )
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.family_connected, status.patientName.orEmpty()),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(via, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(
                stringResource(
                    if (status.talking) R.string.family_talking else R.string.family_talk,
                    status.patientName.orEmpty(),
                ),
                onTalk,
                modifier = Modifier.fillMaxWidth(),
                style = if (status.talking) ActionStyle.DANGER else ActionStyle.PRIMARY,
                icon = if (status.talking) Icons.Filled.Stop else Icons.Filled.Mic,
            )
            Text(stringResource(R.string.family_clean_audio), style = MaterialTheme.typography.bodyMedium)
            ActionButton(
                stringResource(R.string.family_leave),
                onLeave,
                modifier = Modifier.fillMaxWidth(),
                style = ActionStyle.SECONDARY,
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    action: String,
    onAction: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            ActionButton(action, onAction, modifier = Modifier.fillMaxWidth())
            secondary?.let { (label, onClick) ->
                ActionButton(label, onClick, modifier = Modifier.fillMaxWidth(), style = ActionStyle.SECONDARY)
            }
        }
    }
}

private fun scanOptions(): ScanOptions =
    ScanOptions()
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setBeepEnabled(false)
        .setOrientationLocked(false)
        .setPrompt("")

private fun talkPermissionList(): Array<String> =
    buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
