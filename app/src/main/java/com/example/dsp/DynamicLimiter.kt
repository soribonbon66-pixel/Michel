package com.example.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Stereo-linked lookahead limiter.
 *
 * The audio path is delayed by lookaheadSamples. A monotonic deque tracks the
 * maximum linked-stereo peak over that same window, so the gain applied to the
 * delayed frame is informed by peaks occurring up to lookaheadMs in its future.
 * Attack is immediate (the point of lookahead); release is smoothed to avoid
 * abrupt gain pumping. Output is hard-bounded to the configured ceiling.
 */
class DynamicLimiter(
    private var sampleRate: Float = 44100f,
    private var thresholdDb: Float = -6.0f,
    private var ceilingDb: Float = -0.2f,
    private var releaseMs: Float = 50.0f,
    private var lookaheadMs: Float = 3.0f
) {
    var latencyFrames: Int = calculateLookahead(sampleRate, lookaheadMs)
        private set

    private var delayBufferL = FloatArray(latencyFrames)
    private var delayBufferR = FloatArray(latencyFrames)
    private var delayIndex = 0

    // Monotonic maximum deque, indexed by absolute input frame.
    private var dequeCapacity = latencyFrames + 2
    private var peakIndices = LongArray(dequeCapacity)
    private var peakValues = FloatArray(dequeCapacity)
    private var dequeHead = 0
    private var dequeCount = 0
    private var frameIndex = 0L

    private var releaseCoeff = releaseCoefficient(releaseMs, sampleRate)
    private var ceilingLinear = 10.0f.pow(ceilingDb / 20.0f)
    private var thresholdLinear = 10.0f.pow(thresholdDb / 20.0f)
    private var currentGain = 1.0f

    var currentGainReductionDb: Float = 0.0f
        private set
    var maxGainReductionDb: Float = 0.0f
        private set

    fun configure(
        sampleRate: Float = this.sampleRate,
        thresholdDb: Float = this.thresholdDb,
        ceilingDb: Float = this.ceilingDb,
        releaseMs: Float = this.releaseMs
    ) {
        val newLatency = calculateLookahead(sampleRate, lookaheadMs)
        val latencyChanged = newLatency != latencyFrames
        this.sampleRate = sampleRate.coerceAtLeast(8000f)
        this.thresholdDb = thresholdDb.coerceIn(-20f, 0f)
        this.ceilingDb = ceilingDb.coerceIn(-6f, -0.1f)
        this.releaseMs = releaseMs.coerceIn(5f, 1000f)
        latencyFrames = newLatency
        if (latencyChanged) {
            delayBufferL = FloatArray(latencyFrames)
            delayBufferR = FloatArray(latencyFrames)
            dequeCapacity = latencyFrames + 2
            peakIndices = LongArray(dequeCapacity)
            peakValues = FloatArray(dequeCapacity)
        }
        releaseCoeff = releaseCoefficient(this.releaseMs, this.sampleRate)
        ceilingLinear = 10.0f.pow(this.ceilingDb / 20.0f)
        thresholdLinear = 10.0f.pow(this.thresholdDb / 20.0f)
        reset()
    }

    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        delayIndex = 0
        peakIndices.fill(0L)
        peakValues.fill(0f)
        dequeHead = 0
        dequeCount = 0
        frameIndex = 0L
        currentGain = 1.0f
        currentGainReductionDb = 0.0f
        maxGainReductionDb = 0.0f
    }

    /** Processes one stereo frame in-place. */
    fun processSample(samplePair: FloatArray) {
        val inL = finiteOrZero(samplePair[0])
        val inR = finiteOrZero(samplePair[1])
        val delayedL = delayBufferL[delayIndex]
        val delayedR = delayBufferR[delayIndex]
        delayBufferL[delayIndex] = inL
        delayBufferR[delayIndex] = inR
        delayIndex = (delayIndex + 1) % latencyFrames

        val peak = max(abs(inL), abs(inR))
        pushPeak(frameIndex, peak)
        val oldestAllowed = frameIndex - latencyFrames
        while (dequeCount > 0 && peakIndices[dequeHead] < oldestAllowed) {
            dequeHead = (dequeHead + 1) % dequeCapacity
            dequeCount--
        }
        val windowPeak = if (dequeCount > 0) peakValues[dequeHead] else peak

        // Threshold controls when limiting begins; the ceiling remains the hard maximum.
        val trigger = min(thresholdLinear, ceilingLinear)
        val targetGain = if (windowPeak > trigger && windowPeak > 1e-12f) {
            min(1.0f, min(trigger, ceilingLinear) / windowPeak)
        } else {
            1.0f
        }

        // Immediate attack is safe here because the detector is looking ahead;
        // release is smoothed to reduce audible pumping.
        currentGain = if (targetGain < currentGain) targetGain
        else releaseCoeff * currentGain + (1.0f - releaseCoeff) * targetGain

        val outL = delayedL * currentGain
        val outR = delayedR * currentGain
        samplePair[0] = outL.coerceIn(-ceilingLinear, ceilingLinear)
        samplePair[1] = outR.coerceIn(-ceilingLinear, ceilingLinear)

        val grDb = (-20.0f * log10(max(1e-6f, currentGain))).coerceAtLeast(0f)
        currentGainReductionDb = grDb
        if (grDb > maxGainReductionDb) maxGainReductionDb = grDb
        frameIndex++
    }

    private fun pushPeak(index: Long, value: Float) {
        while (dequeCount > 0) {
            val tail = (dequeHead + dequeCount - 1 + dequeCapacity) % dequeCapacity
            if (peakValues[tail] > value) break
            dequeCount--
        }
        val tail = (dequeHead + dequeCount) % dequeCapacity
        peakIndices[tail] = index
        peakValues[tail] = value
        dequeCount++
    }

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f

    private fun calculateLookahead(rate: Float, ms: Float): Int =
        (ms * rate / 1000f).toInt().coerceIn(16, 2048)

    private fun releaseCoefficient(ms: Float, rate: Float): Float =
        exp(-1.0f / (ms.coerceAtLeast(1f) * 0.001f * rate.coerceAtLeast(8000f)))
}
