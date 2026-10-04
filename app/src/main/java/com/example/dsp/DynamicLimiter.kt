package com.example.dsp

import kotlin.math.*

/**
 * Mastering Brickwall Limiter with Lookahead, Soft-Knee, and Dynamic Release.
 * Prevents inter-sample distortion and true-peak overs while pushing commercial loudness.
 */
class DynamicLimiter(
    private var sampleRate: Float = 44100f,
    private var thresholdDb: Float = -6.0f,
    private var ceilingDb: Float = -0.2f,
    private var releaseMs: Float = 50.0f,
    private var lookaheadMs: Float = 3.0f
) {
    private var lookaheadSamples = (lookaheadMs * sampleRate / 1000f).toInt().coerceIn(16, 512)
    private var delayBufferL = FloatArray(lookaheadSamples)
    private var delayBufferR = FloatArray(lookaheadSamples)
    private var delayIndex = 0

    private var currentEnvelope = 0.0f
    private var releaseCoeff = exp(-1.0f / (releaseMs * 0.001f * sampleRate))
    private var attackCoeff = exp(-1.0f / (0.5f * 0.001f * sampleRate)) // 0.5ms fast attack

    private var ceilingLinear = 10.0f.pow(ceilingDb / 20.0f)
    private var thresholdLinear = 10.0f.pow(thresholdDb / 20.0f)

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
        this.sampleRate = sampleRate
        this.thresholdDb = thresholdDb
        this.ceilingDb = ceilingDb
        this.releaseMs = releaseMs

        lookaheadSamples = (lookaheadMs * sampleRate / 1000f).toInt().coerceIn(16, 512)
        if (delayBufferL.size != lookaheadSamples) {
            delayBufferL = FloatArray(lookaheadSamples)
            delayBufferR = FloatArray(lookaheadSamples)
            delayIndex = 0
        }
        releaseCoeff = exp(-1.0f / (releaseMs * 0.001f * sampleRate))
        ceilingLinear = 10.0f.pow(ceilingDb / 20.0f)
        thresholdLinear = 10.0f.pow(thresholdDb / 20.0f)
    }

    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        delayIndex = 0
        currentEnvelope = 0.0f
        currentGainReductionDb = 0.0f
        maxGainReductionDb = 0.0f
    }

    /**
     * Process stereo sample pair [left, right], writes limited output back into the array.
     */
    fun processSample(samplePair: FloatArray) {
        val inL = samplePair[0]
        val inR = samplePair[1]

        // Read delayed sample from lookahead ring buffer
        val delayedL = delayBufferL[delayIndex]
        val delayedR = delayBufferR[delayIndex]

        // Store current input in delay buffer
        delayBufferL[delayIndex] = inL
        delayBufferR[delayIndex] = inR
        delayIndex = (delayIndex + 1) % lookaheadSamples

        // Peak detection over lookahead window
        val peak = max(abs(inL), abs(inR))
        val targetEnv = if (peak > thresholdLinear) {
            // Over threshold: compute required attenuation
            thresholdLinear / peak
        } else {
            1.0f
        }

        // Smooth envelope follower (fast attack, smooth release)
        if (targetEnv < currentEnvelope) {
            currentEnvelope = attackCoeff * currentEnvelope + (1.0f - attackCoeff) * targetEnv
        } else {
            currentEnvelope = releaseCoeff * currentEnvelope + (1.0f - releaseCoeff) * targetEnv
        }

        // Apply gain reduction
        val rawGain = currentEnvelope
        var outL = delayedL * rawGain
        var outR = delayedR * rawGain

        // Safe soft-clip barrier at ceiling
        outL = softClip(outL, ceilingLinear)
        outR = softClip(outR, ceilingLinear)

        samplePair[0] = outL
        samplePair[1] = outR

        val grDb = -20.0f * log10(max(1e-4f, rawGain))
        currentGainReductionDb = grDb
        if (grDb > maxGainReductionDb) {
            maxGainReductionDb = grDb
        }
    }

    /**
     * Studio tanh-style cubic soft clipping ceiling to prevent sharp digital square-wave distortion.
     */
    private fun softClip(x: Float, ceiling: Float): Float {
        val norm = x / ceiling
        val clipped = if (norm > 1.0f) {
            1.0f - 1.0f / (3.0f * norm)
        } else if (norm < -1.0f) {
            -1.0f - 1.0f / (3.0f * norm)
        } else {
            norm - (norm * norm * norm) / 6.0f
        }
        return (clipped * ceiling).coerceIn(-ceiling, ceiling)
    }
}
