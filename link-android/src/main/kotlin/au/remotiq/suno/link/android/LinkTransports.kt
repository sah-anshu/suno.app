// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import au.remotiq.suno.link.JoinCode
import au.remotiq.suno.link.LinkChannel
import au.remotiq.suno.link.LinkServer

/** Which radio a link uses. */
enum class LinkTransport { WIFI_DIRECT, BLUETOOTH }

/** Opens a channel to whoever showed [JoinCode]: Wi-Fi Direct first, then Bluetooth LE. */
@RequiresApi(Build.VERSION_CODES.Q)
class LinkConnector(
    private val context: Context,
) {
    private var wifi: WifiDirectJoiner? = null

    suspend fun open(code: JoinCode): Pair<LinkChannel, LinkTransport>? {
        code.wifiDirect?.let { credentials ->
            val joiner = WifiDirectJoiner(context)
            runCatching { joiner.join(credentials) }.getOrNull()?.let {
                wifi = joiner
                return it to LinkTransport.WIFI_DIRECT
            }
            joiner.leave()
        }
        if (code.bluetooth) {
            runCatching { BluetoothJoiner(context).join(code.sessionId) }.getOrNull()?.let {
                return it to LinkTransport.BLUETOOTH
            }
        }
        return null
    }

    fun leave() {
        wifi?.leave()
        wifi = null
    }
}

/** Hosts [code] on Wi-Fi Direct and Bluetooth LE for [listener]. */
@RequiresApi(Build.VERSION_CODES.Q)
class LinkHosting(
    private val context: Context,
    private val code: JoinCode,
) {
    private var wifi: WifiDirectHost? = null
    private var bluetooth: BluetoothHost? = null

    /** Returns which radios started. */
    suspend fun start(listener: LinkServer.Listener): Set<LinkTransport> {
        val started = mutableSetOf<LinkTransport>()
        code.wifiDirect?.let { credentials ->
            runCatching { WifiDirectHost(context, credentials).apply { start(listener) } }.getOrNull()?.let {
                wifi = it
                started += LinkTransport.WIFI_DIRECT
            }
        }
        if (code.bluetooth) {
            runCatching { BluetoothHost(context, code.sessionId).apply { start(listener) } }.getOrNull()?.let {
                bluetooth = it
                started += LinkTransport.BLUETOOTH
            }
        }
        return started
    }

    fun stop() {
        wifi?.stop()
        bluetooth?.stop()
        wifi = null
        bluetooth = null
    }
}
