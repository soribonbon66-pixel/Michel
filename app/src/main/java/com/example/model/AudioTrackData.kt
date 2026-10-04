package com.example.model

/**
 * Representation of decoded in-memory audio data in 32-bit floating point format.
 * Samples are interleaved: [L0, R0, L1, R1, ...] if channels == 2.
 */
data class AudioTrackData(
    val title: String,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val samples: FloatArray,
    val durationSeconds: Float = samples.size.toFloat() / (sampleRate * channels)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioTrackData
        return title == other.title &&
                sampleRate == other.sampleRate &&
                channels == other.channels &&
                samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + samples.contentHashCode()
        return result
    }
}
