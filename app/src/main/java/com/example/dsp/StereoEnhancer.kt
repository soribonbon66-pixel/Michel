package com.example.dsp

/**
 * Mid/Side processing matrix with Mono Sub Bass (0 - 90Hz).
 * Ensures low-end compatibility on club subwoofers while expanding stereo width in the highs.
 */
class StereoEnhancer(
    private var sampleRate: Float = 44100f,
    private var monoCutoffHz: Float = 90.0f,
    private var widthFactor: Float = 1.15f
) {
    // High-pass filter applied to the Side channel so bass remains strictly Mono
    private val sideHighPass = BiquadFilter(
        type = BiquadFilter.Type.HIGH_PASS,
        frequency = monoCutoffHz,
        gainDb = 0f,
        q = 0.707f,
        sampleRate = sampleRate
    )

    fun configure(sampleRate: Float, monoCutoffHz: Float, widthFactor: Float) {
        this.sampleRate = sampleRate
        this.monoCutoffHz = monoCutoffHz
        this.widthFactor = widthFactor.coerceIn(0.5f, 2.0f)
        sideHighPass.configure(
            type = BiquadFilter.Type.HIGH_PASS,
            frequency = monoCutoffHz,
            gainDb = 0f,
            q = 0.707f,
            sampleRate = sampleRate
        )
    }

    fun reset() {
        sideHighPass.resetState()
    }

    /**
     * Convert Stereo [L, R] to Mid/Side, filter sub on Side, apply width, convert back to [L, R].
     */
    fun processSample(samplePair: FloatArray) {
        val l = samplePair[0]
        val r = samplePair[1]

        // Encode to M/S
        val mid = (l + r) * 0.5f
        var side = (l - r) * 0.5f

        // Remove sub-bass frequencies from Side channel (Mono bass below 90Hz)
        side = sideHighPass.processSample(side, 0)

        // Expand stereo width
        side *= widthFactor

        // Decode back to Left / Right
        samplePair[0] = mid + side
        samplePair[1] = mid - side
    }
}
