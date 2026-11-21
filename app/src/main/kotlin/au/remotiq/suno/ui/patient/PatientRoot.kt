// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.remotiq.suno.R
import au.remotiq.suno.assist.LiveAudioService
import au.remotiq.suno.patient.Place
import au.remotiq.suno.ui.SunoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class PatientRoute { HOME, CHECK_PIN, SET_PIN, HELPER }

/**
 * Entry point of the patient app. The patient sees only the simple Home. The helper reaches
 * EarCast's full screens (Listen / Profiles / Settings) through the PIN, and Lock returns to Home.
 */
@Composable
fun PatientRoot(viewModel: PatientStateModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(PatientRoute.HOME) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    val session = remember { PatientSession(context, scope, viewModel) { message = it } }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            session.onPermissionResult(result[Manifest.permission.RECORD_AUDIO] == true)
        }
    session.requestPermission = { permissionLauncher.launch(listeningPermissions()) }

    val latestState by rememberUpdatedState(state)
    val earphones = rememberEarphones(context) { if (latestState.autoStartReady) session.autoStart() }

    if (!state.loaded) return

    PatientTheme(state.theme) {
        when {
            route == PatientRoute.HELPER -> HelperMode(onLock = { route = PatientRoute.HOME })
            route == PatientRoute.SET_PIN || route == PatientRoute.CHECK_PIN ->
                PinRoute(route == PatientRoute.SET_PIN, state.hasPin, viewModel) { route = it }
            !state.hasPin || !state.consentAccepted ->
                WelcomeScreen(onStart = { route = if (state.hasPin) PatientRoute.CHECK_PIN else PatientRoute.SET_PIN })
            else ->
                PatientHome(
                    state = state,
                    earphones = earphones,
                    message = message,
                    onToggle = { session.toggle(state) },
                    onPlaceApplied = session::restartIf,
                    onHelper = { route = PatientRoute.CHECK_PIN },
                    viewModel = viewModel,
                )
        }
    }
}

/** Starts and stops listening for the patient Home. The service needs a Context and permissions. */
private class PatientSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val viewModel: PatientStateModel,
    private val showMessage: (Int?) -> Unit,
) {
    var requestPermission: () -> Unit = {}

    fun toggle(state: PatientUiState) {
        when {
            state.running || state.connecting -> LiveAudioService.stop(context)
            !micGranted(context) -> requestPermission()
            else -> start()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) start() else showMessage(R.string.patient_mic_needed)
    }

    fun autoStart() {
        if (micGranted(context)) start()
    }

    /** Places change the noise settings, which apply when a session starts. */
    fun restartIf(needsRestart: Boolean) {
        if (!needsRestart) return
        LiveAudioService.stop(context)
        scope.launch {
            viewModel.awaitStopped()
            start()
        }
    }

    private fun start() {
        if (!headphonesPresent(context)) {
            showMessage(R.string.patient_plug_body)
            return
        }
        showMessage(null)
        scope.launch { if (viewModel.prepare()) LiveAudioService.start(context) }
    }
}

@Composable
private fun PatientHome(
    state: PatientUiState,
    earphones: Boolean,
    message: Int?,
    onToggle: () -> Unit,
    onPlaceApplied: (Boolean) -> Unit,
    onHelper: () -> Unit,
    viewModel: PatientStateModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var placeSheet by rememberSaveable { mutableStateOf(false) }
    PatientHomeScreen(
        state = state,
        device = DeviceStatus(earphones = earphones, batteryPercent = batteryPercent(context)),
        placeSheetOpen = placeSheet,
        onListen = onToggle,
        onVolume = viewModel::changeVolume,
        onOpenPlaces = { placeSheet = true },
        onClosePlaces = { placeSheet = false },
        onPlace = { place: Place ->
            placeSheet = false
            scope.launch { onPlaceApplied(viewModel.choosePlace(place)) }
        },
        onHelper = onHelper,
        message =
            message?.let { stringResource(it) }
                ?: if (state.failed) stringResource(R.string.patient_stopped) else null,
    )
}

@Composable
private fun PinRoute(
    setMode: Boolean,
    hasPin: Boolean,
    viewModel: PatientStateModel,
    navigate: (PatientRoute) -> Unit,
) {
    if (setMode) {
        PinScreen(
            setMode = true,
            onComplete = { pin ->
                viewModel.setPin(pin)
                navigate(PatientRoute.HELPER)
                true
            },
            onBack = if (hasPin) ({ navigate(PatientRoute.HOME) }) else null,
        )
    } else {
        BackHandler { navigate(PatientRoute.HOME) }
        PinScreen(
            setMode = false,
            onComplete = { pin ->
                val ok = viewModel.checkPin(pin)
                if (ok) navigate(PatientRoute.HELPER)
                ok
            },
            onBack = { navigate(PatientRoute.HOME) },
        )
    }
}

/**
 * Whether earphones are connected, updated while Suno is open. [onConnected] runs when earphones
 * appear, for auto-start.
 */
@Composable
private fun rememberEarphones(
    context: Context,
    onConnected: () -> Unit,
): Boolean {
    var earphones by remember { mutableStateOf(headphonesPresent(context)) }
    val latestOnConnected by rememberUpdatedState(onConnected)
    DisposableEffect(Unit) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                    val wasConnected = earphones
                    earphones = headphonesPresent(context)
                    if (!wasConnected && earphones) latestOnConnected()
                }

                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                    earphones = headphonesPresent(context)
                }
            }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return earphones
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    val c = LocalPatientColors.current
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Hearing, null, tint = c.on, modifier = Modifier.size(96.dp))
            Text(
                stringResource(R.string.patient_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.patient_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = c.muted,
                textAlign = TextAlign.Center,
            )
            WideButton(stringResource(R.string.patient_welcome_start), onStart, primary = true)
        }
    }
}

/** EarCast's full screens for the helper, with a bar to lock and return to the patient's Home. */
@Composable
private fun HelperMode(onLock: () -> Unit) {
    val c = LocalPatientColors.current
    Column(Modifier.fillMaxSize()) {
        Surface(color = c.on, contentColor = c.onInk) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.helper_mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = c.onInk,
                    contentColor = c.on,
                    shape = MaterialTheme.shapes.extraLarge,
                    onClick = onLock,
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, null, modifier = Modifier.size(22.dp))
                        Text(
                            stringResource(R.string.helper_lock),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) { SunoApp() }
    }
}

private val HEADPHONE_TYPES =
    buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

private fun headphonesPresent(context: Context): Boolean {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
}

private fun micGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun listeningPermissions(): Array<String> =
    buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

private fun batteryPercent(context: Context): Int? {
    val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
    return battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
}
