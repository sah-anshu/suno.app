// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link.android

import android.annotation.SuppressLint
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
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/** Advert format shared by host and companion: Suno service UUID with `[session id: 8][PSM: 2]`. */
internal object BluetoothAdvert {
    val SERVICE: ParcelUuid = ParcelUuid(UUID.fromString("7d3b3e2a-5a1f-4c8e-9b1e-53756e6f0001"))
    const val ID_BYTES = 8

    /** Match the session id, ignore the channel number. */
    val ID_MASK: ByteArray = ByteArray(ID_BYTES) { -1 } + byteArrayOf(0, 0)

    fun sessionBytes(sessionId: String): ByteArray = sessionId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun encode(
        sessionId: String,
        psm: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(ID_BYTES + 2)
            .put(sessionBytes(sessionId))
            .putShort(psm.toShort())
            .array()

    /** PSM from an advert for [sessionId], or null if it belongs to someone else. */
    fun psmFor(
        sessionId: String,
        data: ByteArray?,
    ): Int? {
        if (data == null || data.size < ID_BYTES + 2) return null
        if (!data.copyOfRange(0, ID_BYTES).contentEquals(sessionBytes(sessionId))) return null
        return ByteBuffer.wrap(data, ID_BYTES, 2).short.toInt() and PSM_MASK
    }

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
                .addServiceData(BluetoothAdvert.SERVICE, BluetoothAdvert.encode(sessionId, socket.psm))
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

/** Finds the patient's advert for a session id and opens an L2CAP channel to it. */
@RequiresApi(Build.VERSION_CODES.Q)
class BluetoothJoiner(
    context: Context,
) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @SuppressLint("MissingPermission")
    suspend fun join(sessionId: String): LinkChannel {
        val scanner = checkNotNull(adapter.bluetoothLeScanner) { "Bluetooth is off" }
        val (device, psm) =
            withTimeout(SCAN_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val callback =
                        object : ScanCallback() {
                            override fun onScanResult(
                                type: Int,
                                result: ScanResult,
                            ) {
                                val psm =
                                    BluetoothAdvert.psmFor(
                                        sessionId,
                                        result.scanRecord?.getServiceData(BluetoothAdvert.SERVICE),
                                    )
                                if (psm != null && cont.isActive) {
                                    runCatching { scanner.stopScan(this) }
                                    cont.resume(result.device to psm)
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
        val socket = device.createInsecureL2capChannel(psm)
        withContext(Dispatchers.IO) { socket.connect() }
        return StreamChannel(socket.inputStream, socket.outputStream, socket::close, preferredCodec = CodecIds.OPUS)
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 20_000L
    }
}
