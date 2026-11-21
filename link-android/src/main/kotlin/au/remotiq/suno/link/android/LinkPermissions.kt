// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/** Runtime permissions the link needs, by role and Android version. */
object LinkPermissions {
    /** Wi-Fi Direct join by name and passphrase, BLE L2CAP and the Opus encoder all need Android 10. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** The patient's phone hosts a Wi-Fi Direct group and advertises over Bluetooth LE. */
    fun host(): List<String> =
        buildList {
            addAll(wifi())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

    /** A companion joins a Wi-Fi Direct group or scans for the patient's Bluetooth LE advert. */
    fun companion(): List<String> =
        buildList {
            addAll(wifi())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.distinct()

    fun missing(
        context: Context,
        permissions: List<String>,
    ): List<String> = permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun wifi(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            // Before Android 13, Wi-Fi Direct and Bluetooth LE scanning require location permission.
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}
