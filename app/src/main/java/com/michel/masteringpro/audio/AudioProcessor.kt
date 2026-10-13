package com.michel.masteringpro.audio

import com.michel.masteringpro.dsp.Compressor
import com.michel.masteringpro.dsp.Eq
import com.michel.masteringpro.dsp.Limiter
import com.michel.masteringpro.dsp.LoudnessEbuR128
import com.michel.masteringpro.dsp.TruePeakMeter
import com.michel.masteringpro.model.AudioTrackData
import com.michel.masteringpro.model.MasteringSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.pow

/** Offline DSP chain: input gain → RBJ EQ → feed-forward compression → limiting → -14 LUFS normalization. */
object AudioProcessor {
    suspend fun process(
        input: AudioTrackData,
        settings: MasteringSettings,
        onProgress: (Float) -> Unit = {}
    ): AudioTrackData {
        val frames = input.frameCount
        val channels = input.channels
        val output = FloatArray(input.samples.size)
        val eq = Eq(input.sampleRate, settings)
        val compressor = if (settings.compressorEnabled) Compressor(
            input.sampleRate,
            settings.compressorThresholdDb,
            settings.compressorRatio,
            settings.compressorAttackMs,
            settings.compressorReleaseMs
        ) else null
        val limiter = if (settings.limiterEnabled) Limiter(
            input.sampleRate,
            settings.limiterThresholdDb,
            settings.limiterReleaseMs
        ) else null
        val inputGain = 10.0.pow(settings.gainDb.coerceIn(-24f, 24f).toDouble() / 20.0).toFloat()
        val checkEvery = (frames / 100).coerceAtLeast(2_048)
        val coroutineContext = currentCoroutineContext()

        for (frame in 0 until frames) {
            val offset = frame * channels
            var left = input.samples[offset] * inputGain
            var right = if (channels > 1) input.samples[offset + 1] * inputGain else left
            eq.processFrame(left, right, channels)
            left = eq.outputLeft
            right = eq.outputRight
            if (compressor != null) {
                compressor.processFrame(left, right, channels)
                left = compressor.outputLeft
                right = compressor.outputRight
            }
            if (limiter != null) {
                limiter.processFrame(left, right, channels)
                left = limiter.outputLeft
                right = limiter.outputRight
            }
            output[offset] = left
            if (channels > 1) output[offset + 1] = right

            if (frame % checkEvery == 0) {
                coroutineContext.ensureActive()
                onProgress(frame.toFloat() / frames.coerceAtLeast(1))
            }
        }
        onProgress(0.92f)

        // LUFS normalization intentionally follows the requested limiter stage. A final global
        // true-peak trim preserves dynamics and keeps the delivered file safely below -1 dBTP.
        val beforeNormalization = LoudnessEbuR128.measure(output, input.sampleRate, channels).integratedLufs
        if (beforeNormalization > -69f) {
            val normalizationDb = (settings.targetLufs - beforeNormalization).coerceIn(-24f, 24f)
            val normalizationGain = 10.0.pow(normalizationDb.toDouble() / 20.0).toFloat()
            for (i in output.indices) output[i] *= normalizationGain
        }
        coroutineContext.ensureActive()
        val measuredPeak = TruePeakMeter.measure(output, channels).truePeak
        val measuredPeakDb = TruePeakMeter.dbfs(measuredPeak)
        if (measuredPeakDb > -1.05f) {
            val safetyGain = 10.0.pow((-1.05f - measuredPeakDb).toDouble() / 20.0).toFloat()
            for (i in output.indices) output[i] *= safetyGain
        }
        onProgress(1f)
        return input.copy(title = "${input.title} · Michel Master", samples = output)
    }
}
