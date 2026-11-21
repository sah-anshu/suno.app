// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.audiograph

/**
 * Re-orders network voice frames by sequence number and smooths arrival jitter.
 *
 * Playback starts once [targetFrames] are queued. A missing frame is concealed by repeating the
 * last frame at falling gain, then silence. If the queue grows past [maxFrames] (the sender's clock
 * runs fast, or a burst arrives), the oldest frames are dropped so delay stays bounded.
 *
 * Thread-safe: the network thread pushes and the audio thread pulls.
 */
class JitterBuffer(
    private val frameSamples: Int,
    private val targetFrames: Int = 3,
    private val maxFrames: Int = 10,
) {
    init {
        require(frameSamples > 0 && targetFrames >= 1 && maxFrames > targetFrames)
    }

    private val queue = java.util.TreeMap<Long, FloatArray>()
    private var nextSequence: Long? = null
    private var playing = false
    private var lastFrame: FloatArray? = null
    private var concealed = 0

    /** Frames waiting to be played. */
    val queued: Int get() = synchronized(this) { queue.size }

    fun push(
        sequence: Long,
        samples: FloatArray,
    ) {
        require(samples.size == frameSamples) { "Expected $frameSamples samples, got ${samples.size}" }
        synchronized(this) {
            val next = nextSequence
            if (next != null && sequence < next) return // late or duplicate
            queue[sequence] = samples.copyOf()
            while (queue.size > maxFrames) queue.pollFirstEntry()
            if (queue.size > maxFrames - 1 && playing) trimTo(targetFrames)
        }
    }

    /**
     * The next frame to play, or null while not playing (nothing to say, or still pre-buffering).
     */
    fun pull(): FloatArray? =
        synchronized(this) {
            if (!playing) {
                if (queue.size < targetFrames) return null
                playing = true
                nextSequence = queue.firstKey()
            }
            val next = nextSequence ?: return null
            val frame = queue.remove(next)
            nextSequence = next + 1
            if (frame != null) {
                concealed = 0
                lastFrame = frame
                return frame
            }
            conceal()
        }

    fun reset() {
        synchronized(this) {
            queue.clear()
            nextSequence = null
            playing = false
            lastFrame = null
            concealed = 0
        }
    }

    private fun conceal(): FloatArray? {
        concealed++
        if (concealed > MAX_CONCEALED_FRAMES || (queue.isEmpty() && concealed > 1)) {
            // The talker stopped or the link dropped: go quiet and pre-buffer again.
            playing = false
            nextSequence = null
            lastFrame = null
            return if (queue.isEmpty()) null else FloatArray(frameSamples)
        }
        val last = lastFrame ?: return FloatArray(frameSamples)
        val gain = CONCEAL_GAIN / concealed
        return FloatArray(frameSamples) { last[it] * gain }
    }

    private fun trimTo(frames: Int) {
        while (queue.size > frames) queue.pollFirstEntry()
        nextSequence = queue.firstKey()
    }

    private companion object {
        const val MAX_CONCEALED_FRAMES = 3
        const val CONCEAL_GAIN = 0.5f
    }
}
