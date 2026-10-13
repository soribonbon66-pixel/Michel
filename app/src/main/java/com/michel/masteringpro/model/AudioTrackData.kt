package com.michel.masteringpro.model

/** Interleaved mono or stereo PCM in 32-bit float format. */
data class AudioTrackData(
    val title: String,
    val sampleRate: Int,
    val channels: Int,
    val samples: FloatArray
) {
    val frameCount: Int get() = samples.size / channels
    val durationSeconds: Float get() = if (sampleRate > 0) frameCount.toFloat() / sampleRate else 0f

    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channels == 1 || channels == 2) { "Michel works with mono or stereo PCM" }
        require(samples.size % channels == 0) { "PCM buffer must contain complete frames" }
    }
}
