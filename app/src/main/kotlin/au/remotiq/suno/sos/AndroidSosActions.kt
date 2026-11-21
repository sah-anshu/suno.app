// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import au.remotiq.suno.assist.LiveAudioService
import au.remotiq.suno.link.GeoPoint
import au.remotiq.suno.phone.PhoneCalls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** SOS on a real phone: texts, calls, GPS and the speaker alarm. */
@Singleton
class AndroidSosActions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val calls: PhoneCalls,
    ) : SosActions {
        private val alarm = SosAlarm(context)

        private fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        override fun sendTexts(
            numbers: List<String>,
            body: String,
        ): Boolean {
            if (!granted(Manifest.permission.SEND_SMS)) return false
            val sms =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                } ?: return false
            return runCatching {
                val parts = sms.divideMessage(body)
                numbers.forEach { sms.sendMultipartTextMessage(it, null, parts, null, null) }
            }.isSuccess
        }

        override fun openTexts(
            numbers: List<String>,
            body: String,
        ) {
            val intent =
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";")))
                    .putExtra("sms_body", body)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        override fun call(number: String) = calls.placeCall(number)

        override suspend fun callAndWait(number: String): Boolean? = calls.callAndWait(number)

        override val canCallEmergencyDirectly: Boolean get() = calls.isDefaultPhoneApp()

        /** As the phone app Suno may call the emergency number; otherwise the phone app opens with it. */
        override fun callEmergency(number: String) {
            if (calls.isDefaultPhoneApp()) {
                calls.placeCall(number)
            } else {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override suspend fun locate(): GeoPoint? {
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                return null
            }
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            val fresh =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ) {
                    withTimeoutOrNull(LOCATE_MS) {
                        suspendCancellableCoroutine<Location?> { cont ->
                            val cancel = android.os.CancellationSignal()
                            cont.invokeOnCancellation { cancel.cancel() }
                            manager.getCurrentLocation(LocationManager.GPS_PROVIDER, cancel, context.mainExecutor) {
                                if (cont.isActive) cont.resume(it)
                            }
                        }
                    }
                } else {
                    null
                }
            val best =
                fresh ?: manager
                    .getProviders(true)
                    .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                    .maxByOrNull { it.time }
            return best?.let { GeoPoint(it.latitude, it.longitude, it.accuracy) }
        }

        override fun alarm(on: Boolean): Boolean =
            if (on) {
                alarm.start()
            } else {
                alarm.stop()
                false
            }

        override fun stopListening() = LiveAudioService.stop(context)

        override fun keepRunning(on: Boolean) {
            // While SOS is on, every call rings, even with "contacts only".
            calls.sosActive = on
            if (on) SosService.start(context) else SosService.stop(context)
        }

        private companion object {
            const val LOCATE_MS = 20_000L
        }
    }
