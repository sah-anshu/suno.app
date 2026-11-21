// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Runs on the TV while it shows its setup code. Accepts the patient's join code from a companion
 * that scanned the TV's code, using the same encrypted framing as the family link.
 */
class TvProvisioningHost(
    private val setupCode: JoinCode,
    private val onProvisioned: (JoinCode) -> Unit,
) : LinkServer.Listener {
    override fun onConnection(channel: LinkChannel) {
        channel.start(
            object : LinkChannel.Receiver {
                var crypto: LinkCrypto? = null

                override fun onControl(frame: ByteArray) {
                    val c = crypto
                    if (c == null) {
                        if (frame.size ==
                            LinkCrypto.SALT_BYTES
                        ) {
                            crypto = LinkCrypto(setupCode.secret, frame)
                        } else {
                            channel.close()
                        }
                        return
                    }
                    val message =
                        c.open(Lane.CLIENT_CONTROL, frame)?.let(LinkMessage::decode) as? LinkMessage.Provision ?: return
                    val patient = JoinCode.parse(message.joinUri)?.takeIf { !it.tvSetup } ?: return channel.close()
                    runCatching {
                        channel.sendControl(
                            c.seal(Lane.HOST_CONTROL, LinkMessage.encode(LinkMessage.Provisioned)),
                        )
                    }
                    onProvisioned(patient)
                }

                override fun onAudio(frame: ByteArray) = Unit

                override fun onClosed() = Unit
            },
        )
    }

    override fun onAudio(frame: ByteArray) = Unit
}

/** Companion side of TV setup: sends the patient's join code to the TV whose setup code was scanned. */
class TvProvisioningClient(
    private val setupCode: JoinCode,
    private val patientCode: JoinCode,
    private val random: SecureRandom = SecureRandom(),
) : LinkChannel.Receiver {
    enum class State { IDLE, SENDING, DONE, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    private var crypto: LinkCrypto? = null
    private var channel: LinkChannel? = null

    fun send(channel: LinkChannel) {
        val salt = ByteArray(LinkCrypto.SALT_BYTES).also(random::nextBytes)
        val c = LinkCrypto(setupCode.secret, salt)
        crypto = c
        this.channel = channel
        _state.value = State.SENDING
        channel.start(this)
        channel.sendControl(salt)
        channel.sendControl(c.seal(Lane.CLIENT_CONTROL, LinkMessage.encode(LinkMessage.Provision(patientCode.toUri()))))
    }

    override fun onControl(frame: ByteArray) {
        if (crypto?.open(Lane.HOST_CONTROL, frame)?.let(LinkMessage::decode) == LinkMessage.Provisioned) {
            _state.value = State.DONE
            channel?.close()
        }
    }

    override fun onAudio(frame: ByteArray) = Unit

    override fun onClosed() {
        if (_state.value != State.DONE) _state.value = State.FAILED
    }
}
