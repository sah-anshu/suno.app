// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.tv

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.remotiq.suno.R
import au.remotiq.suno.family.ShareService
import au.remotiq.suno.link.ClientState
import au.remotiq.suno.link.android.LinkPermissions
import au.remotiq.suno.link.android.LinkTransport
import au.remotiq.suno.ui.patient.LocalPatientColors
import au.remotiq.suno.ui.patient.PatientTheme
import au.remotiq.suno.ui.patient.PatientThemeName
import au.remotiq.suno.ui.patient.QrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TvModel
    @Inject
    constructor(
        private val link: TvLink,
    ) : ViewModel() {
        val status: StateFlow<TvStatus> = link.status

        fun start() = link.start()

        fun retry() = link.retry()

        fun reset() = link.reset()
    }

/** Suno TV, readable from the sofa and driven by the remote's arrows and OK button. */
@Composable
fun TvRoot(viewModel: TvModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notAllowed by rememberSaveable { mutableStateOf(false) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.start() }
    LaunchedEffect(Unit) {
        val wanted =
            (LinkPermissions.host() + LinkPermissions.companion() + Manifest.permission.RECORD_AUDIO).distinct() +
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
        val missing = LinkPermissions.missing(context, wanted)
        if (missing.isEmpty()) viewModel.start() else permissions.launch(missing.toTypedArray())
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
    PatientTheme(PatientThemeName.DARK) {
        TvScreen(
            status = status,
            captureNotAllowed = notAllowed,
            onStartSharing = {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                try {
                    consent.launch(manager.createScreenCaptureIntent())
                } catch (e: ActivityNotFoundException) {
                    notAllowed = true
                }
            },
            onStopSharing = { ShareService.stop(context) },
            onRetry = viewModel::retry,
            onReset = viewModel::reset,
        )
    }
}

/** Stateless TV screen (also used by screenshot tests). */
@Composable
fun TvScreen(
    status: TvStatus,
    captureNotAllowed: Boolean,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    val c = LocalPatientColors.current
    val patient = status.patientName.orEmpty()
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.Tv, null, tint = c.on, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                if (status.sharing) StatusPill(stringResource(R.string.tv_sharing_pill))
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val setup = status.setupUri
                if (setup != null) {
                    Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrCode(setup, stringResource(R.string.tv_setup_code), Modifier.fillMaxWidth())
                    }
                } else {
                    Box(Modifier.size(200.dp).background(c.onSoft, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.GraphicEq, null, tint = c.ink, modifier = Modifier.size(96.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvContent(status, patient, captureNotAllowed, onStartSharing, onStopSharing, onRetry, onReset)
                }
            }
            Text(
                stringResource(R.string.tv_speakers_note),
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun TvContent(
    status: TvStatus,
    patient: String,
    captureNotAllowed: Boolean,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    val c = LocalPatientColors.current
    when {
        status.unsupported -> Title(stringResource(R.string.tv_unsupported))
        status.setupUri != null -> {
            Title(stringResource(R.string.tv_title))
            listOf(R.string.tv_step1, R.string.tv_step2, R.string.tv_step3).forEachIndexed { i, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).border(2.dp, c.hairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    }
                    Text(stringResource(step), style = MaterialTheme.typography.titleMedium, color = c.ink)
                }
            }
        }
        status.state == ClientState.CONNECTING -> Title(stringResource(R.string.tv_connecting, patient))
        status.state == ClientState.WAITING_FOR_APPROVAL -> Title(stringResource(R.string.tv_waiting, patient))
        status.state == ClientState.CONNECTED -> {
            Title(stringResource(R.string.tv_connected, patient))
            val via =
                if (status.transport ==
                    LinkTransport.BLUETOOTH
                ) {
                    R.string.tv_via_bluetooth
                } else {
                    R.string.tv_via_wifi
                }
            Text(stringResource(via), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            if (captureNotAllowed) {
                Text(
                    stringResource(R.string.tv_capture_not_allowed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.ink,
                )
            }
            if (status.sharing) {
                TvButton(stringResource(R.string.tv_stop_sharing), onStopSharing, takeFocus = true)
            } else {
                TvButton(stringResource(R.string.tv_start_sharing), onStartSharing, primary = true)
            }
        }
        status.state == ClientState.DENIED -> {
            Title(stringResource(R.string.tv_denied, patient))
            TvButton(stringResource(R.string.tv_set_up_again), onReset, primary = true)
        }
        else -> {
            Title(stringResource(R.string.tv_not_connected, patient))
            TvButton(stringResource(R.string.tv_try_again), onRetry, primary = true)
            TvButton(stringResource(R.string.tv_set_up_again), onReset)
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.displaySmall, color = LocalPatientColors.current.ink)
}

@Composable
private fun StatusPill(text: String) {
    val c = LocalPatientColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = c.onInk,
        modifier = Modifier.background(c.on, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** A large button with a thick ring when the remote's focus is on it. */
@Composable
private fun TvButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    takeFocus: Boolean = primary,
) {
    val c = LocalPatientColors.current
    val interaction = remember { MutableInteractionSource() }
    val requester = remember { FocusRequester() }
    // The remote starts on the main action, so OK works straight away.
    LaunchedEffect(label, takeFocus) { if (takeFocus) runCatching { requester.requestFocus() } }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .widthIn(min = 320.dp)
            .heightIn(min = 72.dp)
            .focusRequester(requester)
            .then(if (focused) Modifier.border(4.dp, c.ink, RoundedCornerShape(50)) else Modifier)
            .padding(6.dp)
            .background(if (primary) c.on else c.surfaceSoft, RoundedCornerShape(50))
            .selectable(
                selected = false,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).focusable(interactionSource = interaction)
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = if (primary) c.onInk else c.ink)
    }
}
