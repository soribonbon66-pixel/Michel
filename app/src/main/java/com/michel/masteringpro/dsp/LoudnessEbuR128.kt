package com.michel.masteringpro.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight BS.1770/EBU R128 loudness measurement.
 * Uses a K-weighting high shelf and high pass, 400 ms blocks with 100 ms hops,
 * the -70 LUFS absolute gate and -10 LU relative gate. LRA uses gated 3-second blocks.
 */
object LoudnessEbuR128 {
    data class Metrics(val integratedLufs: Float, val loudnessRangeLu: Float)
    private data class Chunk(val energy: Double, val frames: Int)

    fun measure(samples: FloatArray, sampleRate: Int, channels: Int): Metrics {
        val frameCount = samples.size / channels
        if (frameCount == 0) return Metrics(-70f, 0f)

        val highPassL = BiquadFilter(BiquadFilter.Type.HIGH_PASS, 38.1355f, 0f, 0.5003f, sampleRate)
        val shelfL = BiquadFilter(BiquadFilter.Type.HIGH_SHELF, 1_681.97f, 4f, 0.707f, sampleRate)
        val highPassR = if (channels > 1) BiquadFilter(BiquadFilter.Type.HIGH_PASS, 38.1355f, 0f, 0.5003f, sampleRate) else null
        val shelfR = if (channels > 1) BiquadFilter(BiquadFilter.Type.HIGH_SHELF, 1_681.97f, 4f, 0.707f, sampleRate) else null

        val chunkFrames = (sampleRate / 10).coerceAtLeast(1)
        val chunks = ArrayList<Chunk>((frameCount / chunkFrames) + 1)
        var chunkEnergy = 0.0
        var chunkCount = 0
        for (frame in 0 until frameCount) {
            val offset = frame * channels
            var left = highPassL.processSample(samples[offset], 0)
            left = shelfL.processSample(left, 0)
            var energy = left.toDouble() * left
            if (channels > 1) {
                var right = highPassR!!.processSample(samples[offset + 1], 0)
                right = shelfR!!.processSample(right, 0)
                energy += right.toDouble() * right
            }
            chunkEnergy += energy
            chunkCount++
            if (chunkCount == chunkFrames) {
                chunks += Chunk(chunkEnergy, chunkCount)
                chunkEnergy = 0.0
                chunkCount = 0
            }
        }
        if (chunkCount > 0) chunks += Chunk(chunkEnergy, chunkCount)

        val blockPowers = ArrayList<Double>()
        val blockChunks = (sampleRate * 0.4 / chunkFrames).roundToInt().coerceAtLeast(1)
        if (frameCount < sampleRate * 0.4) {
            val totalEnergy = chunks.sumOf { it.energy }
            blockPowers += totalEnergy / frameCount
        } else {
            var start = 0
            while (start + blockChunks <= chunks.size) {
                var energy = 0.0
                var frames = 0
                for (i in start until start + blockChunks) {
                    energy += chunks[i].energy
                    frames += chunks[i].frames
                }
                if (frames == blockChunks * chunkFrames) blockPowers += energy / frames
                start++ // 100 ms hop / 75% overlap
            }
        }
        if (blockPowers.isEmpty()) blockPowers += chunks.sumOf { it.energy } / frameCount

        val absolutePowers = blockPowers.filter { lufs(it) >= -70.0 }
        val integrated = if (absolutePowers.isEmpty()) {
            -70f
        } else {
            val preliminary = lufs(absolutePowers.average())
            val relativeGate = preliminary - 10.0
            val gated = absolutePowers.filter { lufs(it) >= relativeGate }
            lufs(gated.ifEmpty { absolutePowers }.average()).toFloat().coerceIn(-70f, 6f)
        }

        val threeSecondChunks = (sampleRate * 3.0 / chunkFrames).roundToInt().coerceAtLeast(1)
        val hopChunks = (sampleRate / chunkFrames).coerceAtLeast(1)
        val shortTermPowers = ArrayList<Double>()
        if (frameCount >= sampleRate * 3 && chunks.size >= threeSecondChunks) {
            var start = 0
            while (start + threeSecondChunks <= chunks.size) {
                var energy = 0.0
                var frames = 0
                for (i in start until start + threeSecondChunks) {
                    energy += chunks[i].energy
                    frames += chunks[i].frames
                }
                if (frames == threeSecondChunks * chunkFrames) shortTermPowers += energy / frames
                start += hopChunks
            }
        }
        val absoluteShortTerm = shortTermPowers.filter { lufs(it) >= -70.0 }
        val lra = if (absoluteShortTerm.size < 2) {
            0f
        } else {
            val relativeGate = lufs(absoluteShortTerm.average()) - 20.0
            val gatedLoudness = absoluteShortTerm
                .map(::lufs)
                .filter { it >= relativeGate }
                .sorted()
            if (gatedLoudness.size < 2) 0f else {
                (percentile(gatedLoudness, 0.95) - percentile(gatedLoudness, 0.10)).toFloat().coerceAtLeast(0f)
            }
        }
        return Metrics(integrated, lra)
    }

    private fun lufs(power: Double): Double =
        -0.691 + 10.0 * ln(power.coerceAtLeast(1e-14)) / ln(10.0)

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return -70.0
        val position = p.coerceIn(0.0, 1.0) * (values.size - 1)
        val low = position.toInt()
        val high = (low + 1).coerceAtMost(values.lastIndex)
        val fraction = position - low
        return values[low] * (1.0 - fraction) + values[high] * fraction
    }
}
