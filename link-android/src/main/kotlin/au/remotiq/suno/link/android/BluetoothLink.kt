// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import au.remotiq.suno.link.CodecIds
import au.remotiq.suno.link.LinkChannel
import au.remotiq.suno.link.LinkServer
import au.remotiq.suno.link.StreamChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/** Advert format shared by host and companion: Suno service UUID with `[session id: 8][PSM: 2][flags: 1]`. */
internal object BluetoothAdvert {
    val SERVICE: ParcelUuid = ParcelUuid(UUID.fromString("7d3b3e2a-5a1f-4c8e-9b1e-53756e6f0001"))
    const val ID_BYTES = 8

    /** Set in flags when the advert is a companion relaying for the patient, not the patient's phone. */
    const val FLAG_RELAY = 1

    /** Match the session id, ignore the channel number and flags. */
    val ID_MASK: ByteArray = ByteArray(ID_BYTES) { -1 } + byteArrayOf(0, 0, 0)

    fun sessionBytes(sessionId: String): ByteArray = sessionId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun encode(
        sessionId: String,
        psm: Int,
        relay: Boolean = false,
    ): ByteArray =
        ByteBuffer
            .allocate(ID_BYTES + 3)
            .put(sessionBytes(sessionId))
            .putShort(psm.toShort())
            .put(if (relay) FLAG_RELAY.toByte() else 0)
            .array()

    /** Channel number and relay flag from an advert for [sessionId], or null if it belongs to someone else. */
    fun targetFor(
        sessionId: String,
        data: ByteArray?,
    ): Target? {
        if (data == null || data.size < ID_BYTES + 2) return null
        if (!data.copyOfRange(0, ID_BYTES).contentEquals(sessionBytes(sessionId))) return null
        val psm = ByteBuffer.wrap(data, ID_BYTES, 2).short.toInt() and PSM_MASK
        val relay = data.size > ID_BYTES + 2 && data[ID_BYTES + 2].toInt() and FLAG_RELAY != 0
        return Target(psm, relay)
    }

    data class Target(
        val psm: Int,
        val relay: Boolean,
    )

    private const val PSM_MASK = 0xFFFF
}

/**
 * The patient's Bluetooth LE presence: an insecure L2CAP server (frames are encrypted by the link
 * layer) and an advert carrying the session id and channel number.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class BluetoothHost(
    context: Context,
    private val sessionId: String,
    /** True on a companion relaying for the patient. */
    private val relay: Boolean = false,
) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var server: BluetoothServerSocket? = null
    private var advertising: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun start(listener: LinkServer.Listener) {
        val socket = adapter.listenUsingInsecureL2capChannel()
        server = socket
        thread(name = "suno-ble-accept", isDaemon = true) {
            while (true) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                listener.onConnection(
                    StreamChannel(
                        client.inputStream,
                        client.outputStream,
                        client::close,
                        preferredCodec = CodecIds.OPUS,
                    ),
                )
            }
        }
        val callback = object : AdvertiseCallback() {}
        advertising = callback
        adapter.bluetoothLeAdvertiser?.startAdvertising(
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .build(),
            AdvertiseData
                .Builder()
                .setIncludeDeviceName(false)
                .addServiceData(BluetoothAdvert.SERVICE, BluetoothAdvert.encode(sessionId, socket.psm, relay))
                .build(),
            callback,
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        advertising?.let { runCatching { adapter.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        advertising = null
        runCatching { server?.close() }
        server = null
    }
}

/** Finds the patient's advert for a session id (or a relay's) and opens an L2CAP channel to it. */
@RequiresApi(Build.VERSION_CODES.Q)
class BluetoothJoiner(
    context: Context,
) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var target: Pair<BluetoothDevice, Int>? = null

    /** True when the channel goes through a companion relaying for the patient. */
    var viaRelay = false
        private set

    /** Prefers the patient's own advert; takes a relay only if the patient is not heard soon. */
    @SuppressLint("MissingPermission")
    suspend fun join(sessionId: String): LinkChannel {
        val scanner = checkNotNull(adapter.bluetoothLeScanner) { "Bluetooth is off" }
        var relayFound: Pair<BluetoothDevice, BluetoothAdvert.Target>? = null
        val found =
            withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val callback =
                        object : ScanCallback() {
                            override fun onScanResult(
                                type: Int,
                                result: ScanResult,
                            ) {
                                val advert =
                                    BluetoothAdvert.targetFor(
                                        sessionId,
                                        result.scanRecord?.getServiceData(BluetoothAdvert.SERVICE),
                                    ) ?: return
                                if (advert.relay) {
                                    if (relayFound == null) relayFound = result.device to advert
                                } else if (cont.isActive) {
                                    runCatching { scanner.stopScan(this) }
                                    cont.resume(result.device to advert)
                                }
                            }
                        }
                    scanner.startScan(
                        listOf(
                            ScanFilter
                                .Builder()
                                .setServiceData(
                                    BluetoothAdvert.SERVICE,
                                    BluetoothAdvert.encode(sessionId, 0),
                                    BluetoothAdvert.ID_MASK,
                                ).build(),
                        ),
                        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                        callback,
                    )
                    cont.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
                }
            }
        val (device, advert) = checkNotNull(found ?: relayFound) { "Suno not found nearby" }
        viaRelay = advert.relay
        target = device to advert.psm
        return connectAgain()
    }

    /** Opens one more channel to the same phone (used to relay for others). */
    @SuppressLint("MissingPermission")
    suspend fun connectAgain(): LinkChannel {
        val (device, psm) = checkNotNull(target) { "not joined" }
        val socket = device.createInsecureL2capChannel(psm)
        withContext(Dispatchers.IO) { socket.connect() }
        return StreamChannel(socket.inputStream, socket.outputStream, socket::close, preferredCodec = CodecIds.OPUS)
    }

    private companion object {
        /** Long enough to hear the patient's phone over a nearer relay. */
        const val SCAN_TIMEOUT_MS = 12_000L
    }
}
