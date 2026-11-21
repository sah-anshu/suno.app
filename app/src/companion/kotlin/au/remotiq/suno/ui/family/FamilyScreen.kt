// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.family

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
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
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import au.remotiq.suno.family.ShareService
import au.remotiq.suno.family.TalkService
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.JoinCode
import au.remotiq.suno.link.TvProvisioningClient
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.link.android.LinkTransport
import au.remotiq.suno.link.android.ShareSource
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

        /** A scanned code is either the patient's join code or a TV's setup code. */
        fun onScanned(scanned: String) {
            if (JoinCode.parse(scanned)?.tvSetup == true) link.setUpTv(scanned) else link.join(scanned)
        }

        fun reconnect() = link.reconnect()

        fun leave() = link.leave()
    }

/** Companion app, first tab: join the patient's Suno and talk to them. */
@Composable
fun FamilyScreen(viewModel: FamilyModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val myName by viewModel.myName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scan =
        rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(viewModel::onScanned) }
    val joinPermissions =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { scan.launch(scanOptions()) }
    var pendingShare by remember { mutableStateOf<ShareSource?>(null) }
    val sharePermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val source = pendingShare
            if (granted[Manifest.permission.RECORD_AUDIO] == true && source != null) ShareService.start(context, source)
        }
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK &&
                data != null
            ) {
                ShareService.startPlayback(context, result.resultCode, data)
            }
        }
    val talkPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.RECORD_AUDIO] == true) TalkService.start(context)
        }

    FamilyContent(
        status = status,
        myName = myName,
        onName = viewModel::setMyName,
        onScan = {
            val missing = LinkPermissions.missing(context, LinkPermissions.companion())
            if (missing.isEmpty()) scan.launch(scanOptions()) else joinPermissions.launch(missing.toTypedArray())
        },
        onTalk = {
            if (status.talking) TalkService.stop(context) else talkPermissions.launch(talkPermissionList())
        },
        onLeave = {
            TalkService.stop(context)
            viewModel.leave()
        },
        onReconnect = viewModel::reconnect,
        onShare = { source ->
            pendingShare = source
            if (source == ShareSource.PLAYBACK) {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                consent.launch(manager.createScreenCaptureIntent())
            } else {
                sharePermissions.launch(talkPermissionList())
            }
        },
        onStopShare = { ShareService.stop(context) },
        onSetUpTv = { scan.launch(scanOptions()) },
    )
}

/** Stateless Family tab content (also used by screenshot tests). */
@Composable
fun FamilyContent(
    status: CompanionStatus,
    myName: String,
    onName: (String) -> Unit,
    onScan: () -> Unit,
    onTalk: () -> Unit,
    onLeave: () -> Unit,
    onReconnect: () -> Unit,
    onShare: (ShareSource) -> Unit = {},
    onStopShare: () -> Unit = {},
    onSetUpTv: () -> Unit = {},
) {
    EarPage {
        PageHeading(stringResource(R.string.nav_family), stringResource(R.string.family_subtitle))
        when (status.state) {
            ClientState.IDLE -> JoinCard(myName, onName, onScan)
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
                    onAction = onLeave,
                )
            ClientState.CONNECTED -> {
                ConnectedCard(status, onTalk, onLeave)
                ShareCard(status.sharing, onShare, onStopShare)
                TvSetupCard(status.tvSetup, onSetUpTv)
            }
            ClientState.DENIED ->
                StatusCard(
                    stringResource(R.string.family_denied),
                    stringResource(R.string.family_scan_again),
                    onLeave,
                )
            ClientState.CLOSED ->
                StatusCard(
                    stringResource(R.string.family_not_connected, status.patientName.orEmpty()),
                    stringResource(R.string.family_reconnect),
                    onReconnect,
                    secondary = stringResource(R.string.family_forget) to onLeave,
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
                LinkTransport.BLUETOOTH
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
private fun ShareCard(
    sharing: ShareSource?,
    onShare: (ShareSource) -> Unit,
    onStop: () -> Unit,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
            if (sharing != null) {
                Text(
                    stringResource(R.string.share_now, stringResource(sharing.label())),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionButton(
                    stringResource(R.string.share_stop_sharing),
                    onStop,
                    modifier = Modifier.fillMaxWidth(),
                    style = ActionStyle.DANGER,
                )
            } else {
                Text(stringResource(R.string.share_body), style = MaterialTheme.typography.bodyMedium)
                ShareSource.entries.forEach { source ->
                    ActionButton(
                        stringResource(source.label()),
                        { onShare(source) },
                        modifier = Modifier.fillMaxWidth(),
                        style = ActionStyle.SECONDARY,
                        enabled = source != ShareSource.PLAYBACK || LinkPermissions.supported(),
                    )
                }
            }
        }
    }
}

private fun ShareSource.label(): Int =
    when (this) {
        ShareSource.CABLE -> R.string.share_cable
        ShareSource.MICROPHONE -> R.string.share_microphone
        ShareSource.PLAYBACK -> R.string.share_playback
    }

@Composable
private fun TvSetupCard(
    state: TvProvisioningClient.State,
    onSetUp: () -> Unit,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tv_setup_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    when (state) {
                        TvProvisioningClient.State.IDLE -> R.string.tv_setup_body
                        TvProvisioningClient.State.SENDING -> R.string.tv_setup_sending
                        TvProvisioningClient.State.DONE -> R.string.tv_setup_done
                        TvProvisioningClient.State.FAILED -> R.string.tv_setup_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            ActionButton(
                stringResource(R.string.tv_setup_scan),
                onSetUp,
                modifier = Modifier.fillMaxWidth(),
                style = ActionStyle.SECONDARY,
                enabled = state != TvProvisioningClient.State.SENDING,
                icon = Icons.Filled.Tv,
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
