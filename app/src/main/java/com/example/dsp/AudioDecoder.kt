package com.example.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AudioTrackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Universal Audio Decoder (WAV, MP3, AAC, M4A, FLAC, OGG) using native MediaCodec,
 * plus a built-in procedural Urban Dembow & Reggaeton Drum Machine generator for instant studio audition.
 */
object AudioDecoder {

    /**
     * Decodes any supported Android audio URI into interleaved 32-bit float PCM.
     */
    suspend fun decodeFromUri(context: Context, uri: Uri): AudioTrackData = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            // Fallback for file paths
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw e
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }

        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw IllegalArgumentException("No valid audio stream found in selected file")
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val decodedFloats = ArrayList<Float>(sampleRate * channelCount * 10)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 5000L

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputBufIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inputBufIndex)
                    if (inputBuf != null) {
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val presentationTime = extractor.sampleTime
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufIndex >= 0) {
                val outputBuf = codec.getOutputBuffer(outputBufIndex)
                if (outputBuf != null && bufferInfo.size > 0) {
                    outputBuf.position(bufferInfo.offset)
                    outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuf.order(ByteOrder.LITTLE_ENDIAN)

                    // Standard 16-bit PCM output from MediaCodec
                    val shortBuf = outputBuf.asShortBuffer()
                    val count = shortBuf.remaining()
                    for (k in 0 until count) {
                        val s = shortBuf.get()
                        decodedFloats.add(s / 32768.0f)
                    }
                }
                codec.releaseOutputBuffer(outputBufIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Get file name
        var title = "Audio Track"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    title = cursor.getString(nameIndex) ?: title
                }
            }
        } catch (_: Exception) {}

        val floatArray = FloatArray(decodedFloats.size)
        for (i in decodedFloats.indices) {
            floatArray[i] = decodedFloats[i]
        }

        AudioTrackData(
            title = title,
            sampleRate = sampleRate,
            channels = channelCount.coerceIn(1, 2),
            samples = floatArray
        )
    }

    /**
     * Synthesizes a studio-grade unmastered Dembow/Reggaeton beat loop.
     * Contains deep 808 sub-bass, punchy kick drum, syncopated dembow snare, open hats, and chords.
     * Raw mix is deliberately dynamic and unmastered (-17 LUFS) so mastering difference is dramatic!
     */
    fun generateUrbanDemoBeat(
        isOldSchool: Boolean = false,
        durationSeconds: Float = 12.0f,
        sampleRate: Int = 44100
    ): AudioTrackData {
        val totalFrames = (sampleRate * durationSeconds).toInt()
        val channels = 2
        val samples = FloatArray(totalFrames * channels)

        val bpm = if (isOldSchool) 95f else 118f
        val beatDuration = 60.0f / bpm
        val sixteenth = beatDuration / 4.0f

        // Dembow Tresillo pattern: Kick on beats 1, 2, 3, 4; Snares on dotted sixteenths [0, 3, 6, 10, 12, 14...]
        val totalSixteenths = (durationSeconds / sixteenth).toInt()

        for (step in 0 until totalSixteenths) {
            val stepTime = step * sixteenth
            val startFrame = (stepTime * sampleRate).toInt()
            val stepInBar = step % 16

            // Kick pattern: 0, 4, 8, 12 (four on the floor)
            if (stepInBar % 4 == 0) {
                renderKick(samples, startFrame, sampleRate, totalFrames)
            }

            // Dembow Snare pattern: step 3, 6, 11, 14 (classic "tun-chin-tu-chin")
            if (stepInBar == 3 || stepInBar == 6 || stepInBar == 11 || stepInBar == 14) {
                renderSnare(samples, startFrame, sampleRate, totalFrames, isOldSchool)
            }

            // Hi-hats: on every 16th note with velocity variation
            val hatVelocity = if (stepInBar % 2 == 0) 0.25f else 0.45f
            renderHiHat(samples, startFrame, sampleRate, totalFrames, hatVelocity)

            // Sub-Bass note on kick and syncopated dembow groove
            if (stepInBar == 0 || stepInBar == 6 || stepInBar == 8 || stepInBar == 14) {
                val subFreq = if (stepInBar < 8) 46.25f else 41.20f // F#1 to E1
                renderSubBass(samples, startFrame, sampleRate, totalFrames, subFreq, sixteenth * 3.5f)
            }
        }

        // Add subtle room vinyl hiss and warm background pad
        val rnd = java.util.Random(42)
        for (i in 0 until totalFrames) {
            val t = i.toFloat() / sampleRate
            val pad = (sin(2.0 * Math.PI * 174.61 * t) * 0.04f + sin(2.0 * Math.PI * 220.0 * t) * 0.03f).toFloat()
            val hiss = (rnd.nextFloat() - 0.5f) * 0.005f
            samples[i * 2] = (samples[i * 2] + pad + hiss) * 0.75f
            samples[i * 2 + 1] = (samples[i * 2 + 1] + pad * 0.9f - hiss) * 0.75f
        }

        val name = if (isOldSchool) "Old-School Perreo 95BPM [Raw Mix]" else "Dembow Dominicano 118BPM [Raw Mix]"
        return AudioTrackData(
            title = name,
            sampleRate = sampleRate,
            channels = channels,
            samples = samples
        )
    }

    private fun renderKick(samples: FloatArray, startFrame: Int, sampleRate: Int, totalFrames: Int) {
        val durationFrames = (sampleRate * 0.32f).toInt()
        for (i in 0 until durationFrames) {
            val frame = startFrame + i
            if (frame >= totalFrames) break
            val t = i.toFloat() / sampleRate
            // Pitch drop envelope from 160Hz down to 50Hz
            val pitch = 50.0 + 110.0 * exp(-35.0 * t)
            val amp = exp(-12.0 * t).toFloat()
            // Transient click at the start
            val click = if (i < (sampleRate * 0.005f)) (1.0f - i / (sampleRate * 0.005f)) * 0.35f else 0.0f
            val sample = (sin(2.0 * Math.PI * pitch * t).toFloat() * 0.85f + click) * amp * 0.65f

            samples[frame * 2] += sample
            samples[frame * 2 + 1] += sample
        }
    }

    private fun renderSnare(samples: FloatArray, startFrame: Int, sampleRate: Int, totalFrames: Int, isOldSchool: Boolean) {
        val durationFrames = (sampleRate * 0.22f).toInt()
        val rnd = java.util.Random(startFrame.toLong())
        val toneFreq = if (isOldSchool) 210.0 else 260.0

        for (i in 0 until durationFrames) {
            val frame = startFrame + i
            if (frame >= totalFrames) break
            val t = i.toFloat() / sampleRate
            val toneAmp = exp(-22.0 * t).toFloat()
            val noiseAmp = exp(-14.0 * t).toFloat()

            val tone = sin(2.0 * Math.PI * toneFreq * t).toFloat() * toneAmp * 0.5f
            val noise = (rnd.nextFloat() * 2f - 1f) * noiseAmp * 0.65f
            val sample = (tone + noise) * 0.55f

            samples[frame * 2] += sample * 0.95f
            samples[frame * 2 + 1] += sample * 1.05f // Subtle stereo spread
        }
    }

    private fun renderHiHat(samples: FloatArray, startFrame: Int, sampleRate: Int, totalFrames: Int, velocity: Float) {
        val durationFrames = (sampleRate * 0.05f).toInt()
        val rnd = java.util.Random(startFrame.toLong() * 3)
        for (i in 0 until durationFrames) {
            val frame = startFrame + i
            if (frame >= totalFrames) break
            val t = i.toFloat() / sampleRate
            val amp = exp(-70.0 * t).toFloat()
            val noise = (rnd.nextFloat() * 2f - 1f) * amp * velocity * 0.35f

            samples[frame * 2] += noise * 0.8f
            samples[frame * 2 + 1] += noise * 1.2f
        }
    }

    private fun renderSubBass(
        samples: FloatArray,
        startFrame: Int,
        sampleRate: Int,
        totalFrames: Int,
        freq: Float,
        durationSec: Float
    ) {
        val durationFrames = (sampleRate * durationSec).toInt()
        for (i in 0 until durationFrames) {
            val frame = startFrame + i
            if (frame >= totalFrames) break
            val t = i.toFloat() / sampleRate
            val envelope = (1.0f - exp(-50.0f * t)) * exp(-2.2f * t)
            val sub = sin(2.0 * Math.PI * freq * t).toFloat() * envelope * 0.55f

            samples[frame * 2] += sub
            samples[frame * 2 + 1] += sub
        }
    }
}
