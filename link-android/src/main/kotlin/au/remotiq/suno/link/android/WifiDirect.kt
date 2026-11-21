// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import au.remotiq.suno.link.LinkChannel
import au.remotiq.suno.link.LinkServer
import au.remotiq.suno.link.SocketLinkConnector
import au.remotiq.suno.link.SocketLinkServer
import au.remotiq.suno.link.WifiDirectCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Port the patient's phone listens on inside its private Wi-Fi Direct group (TCP and UDP). */
const val LINK_PORT = 47_620

/**
 * Hosts the patient's private Wi-Fi Direct group with the fixed name and passphrase from the join
 * code, then listens for companions on the group owner address only.
 * Permission (Nearby devices, or location before Android 13) must be granted before [start].
 */
@RequiresApi(Build.VERSION_CODES.Q)
class WifiDirectHost(
    private val context: Context,
    private val credentials: WifiDirectCredentials,
) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var server: SocketLinkServer? = null

    @SuppressLint("MissingPermission")
    suspend fun start(listener: LinkServer.Listener) {
        val ch = manager.initialize(context, Looper.getMainLooper(), null)
        channel = ch
        val config =
            WifiP2pConfig
                .Builder()
                .setNetworkName(credentials.networkName)
                .setPassphrase(credentials.passphrase)
                .build()
        // A group left over from an earlier session would block createGroup.
        runCatching { action { manager.removeGroup(ch, it) } }
        action { manager.createGroup(ch, config, it) }
        val owner = withTimeout(GROUP_TIMEOUT_MS) { groupOwnerAddress(ch) }
        val s = withContext(Dispatchers.IO) { SocketLinkServer(owner, LINK_PORT) }
        s.start(listener)
        server = s
    }

    fun stop() {
        server?.stop()
        server = null
        channel?.let { ch -> manager.removeGroup(ch, null) }
        channel = null
    }

    private suspend fun groupOwnerAddress(ch: WifiP2pManager.Channel): InetAddress {
        while (true) {
            val info =
                suspendCancellableCoroutine<WifiP2pInfo?> { cont ->
                    manager.requestConnectionInfo(ch) { cont.resume(it) }
                }
            val address = info?.groupOwnerAddress
            if (info?.groupFormed == true && info.isGroupOwner && address != null) return address
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    private suspend fun action(block: (WifiP2pManager.ActionListener) -> Unit) =
        suspendCancellableCoroutine { cont ->
            block(
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = cont.resume(Unit)

                    override fun onFailure(reason: Int) =
                        cont.resumeWithException(IllegalStateException("Wi-Fi Direct error $reason"))
                },
            )
        }

    private companion object {
        const val GROUP_TIMEOUT_MS = 15_000L
        const val POLL_MS = 300L
    }
}

/** Joins the patient's Wi-Fi Direct group from the join code, without discovery, then connects. */
@RequiresApi(Build.VERSION_CODES.Q)
class WifiDirectJoiner(
    private val context: Context,
) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    @SuppressLint("MissingPermission")
    suspend fun join(credentials: WifiDirectCredentials): LinkChannel {
        val ch = manager.initialize(context, Looper.getMainLooper(), null)
        channel = ch
        val config =
            WifiP2pConfig
                .Builder()
                .setNetworkName(credentials.networkName)
                .setPassphrase(credentials.passphrase)
                .build()
        val owner =
            withTimeout(JOIN_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val receiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(
                                c: Context,
                                intent: Intent,
                            ) {
                                @Suppress("DEPRECATION")
                                val network = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                                @Suppress("DEPRECATION")
                                val info = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                                val formed = network?.isConnected == true && info?.groupFormed == true
                                val address = info?.groupOwnerAddress?.takeIf { formed }
                                if (address != null && cont.isActive) {
                                    runCatching { context.unregisterReceiver(this) }
                                    cont.resume(address)
                                }
                            }
                        }
                    context.registerReceiver(receiver, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
                    cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                    manager.connect(
                        ch,
                        config,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() = Unit

                            override fun onFailure(reason: Int) {
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        IllegalStateException("Wi-Fi Direct error $reason"),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        return withContext(Dispatchers.IO) { SocketLinkConnector.connect(owner, LINK_PORT) }
    }

    fun leave() {
        channel?.let { ch -> manager.removeGroup(ch, null) }
        channel = null
    }

    private companion object {
        const val JOIN_TIMEOUT_MS = 20_000L
    }
}
