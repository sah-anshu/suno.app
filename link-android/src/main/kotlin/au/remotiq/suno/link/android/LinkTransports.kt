// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import au.remotiq.suno.link.JoinCode
import au.remotiq.suno.link.LinkChannel
import au.remotiq.suno.link.LinkRelay
import au.remotiq.suno.link.LinkServer
import kotlinx.coroutines.CoroutineScope

/** Which radio a link uses. RELAY is Bluetooth through another family member's phone. */
enum class LinkTransport { WIFI_DIRECT, BLUETOOTH, RELAY }

/** Opens a channel to whoever showed [JoinCode]: Wi-Fi Direct first, then Bluetooth LE. */
@RequiresApi(Build.VERSION_CODES.Q)
class LinkConnector(
    private val context: Context,
) {
    private var wifi: WifiDirectJoiner? = null
    private var bluetooth: BluetoothJoiner? = null

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
            val joiner = BluetoothJoiner(context)
            runCatching { joiner.join(code.sessionId) }.getOrNull()?.let {
                bluetooth = joiner
                return it to if (joiner.viaRelay) LinkTransport.RELAY else LinkTransport.BLUETOOTH
            }
        }
        return null
    }

    /** One more connection to the patient's phone, for relaying. Null when not directly connected. */
    suspend fun openAnother(): LinkChannel? =
        runCatching {
            wifi?.connectAgain() ?: bluetooth?.takeIf { !it.viaRelay }?.connectAgain()
        }.getOrNull()

    fun leave() {
        wifi?.leave()
        wifi = null
        bluetooth = null
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

/**
 * Mesh relay on a companion connected directly to the patient: advertises the patient's session on
 * Bluetooth LE with the relay flag, and pipes far phones to the patient through [connector].
 */
@RequiresApi(Build.VERSION_CODES.Q)
class RelayHosting(
    private val context: Context,
    private val code: JoinCode,
    private val connector: LinkConnector,
    private val scope: CoroutineScope,
) {
    private var host: BluetoothHost? = null
    private var relay: LinkRelay? = null

    /** Phones being relayed right now. */
    val active: Int get() = relay?.active ?: 0

    /** Returns false when Bluetooth LE advertising is not available. */
    fun start(): Boolean {
        if (!code.bluetooth) return false
        val linkRelay = LinkRelay(scope, connector::openAnother)
        val started =
            runCatching {
                BluetoothHost(context, code.sessionId, relay = true).apply { start(linkRelay) }
            }.getOrNull()
        host = started
        relay = linkRelay.takeIf { started != null }
        return started != null
    }

    fun stop() {
        host?.stop()
        host = null
        relay?.closeAll()
        relay = null
    }
}
