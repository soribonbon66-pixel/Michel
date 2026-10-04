package com.example.dsp

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AudioTrackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Universal Audio Decoder (WAV, MP3, AAC, M4A, FLAC, OGG) using native MediaCodec + direct WAV engine,
 * plus a built-in procedural Urban Dembow & Reggaeton Drum Machine generator for instant studio audition.
 */
object AudioDecoder {

    private const val MAX_TRACK_DURATION_SECONDS = 420 // 7 minutes maximum for safe mobile memory headroom

    /**
     * Primitive, zero-boxing chunked float buffer to prevent Dalvik OutOfMemoryError.
     * Avoids creating millions of java.lang.Float objects on the garbage collector heap.
     */
    private class PrimitiveFloatBuffer(private val chunkSize: Int = 131072) {
        private val chunks = ArrayList<FloatArray>()
        private var currentChunk = FloatArray(chunkSize)
        private var currentPos = 0
        var totalCount = 0
            private set

        fun add(sample: Float) {
            if (currentPos >= chunkSize) {
                chunks.add(currentChunk)
                currentChunk = FloatArray(chunkSize)
                currentPos = 0
            }
            currentChunk[currentPos++] = sample
            totalCount++
        }

        fun toFloatArray(): FloatArray {
            val result = FloatArray(totalCount)
            var dest = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, result, dest, chunkSize)
                dest += chunkSize
            }
            if (currentPos > 0) {
                System.arraycopy(currentChunk, 0, result, dest, currentPos)
            }
            return result
        }
    }

    /**
     * Decodes any supported Android audio URI into interleaved 32-bit float PCM.
     * Uses an isolated cache file to completely prevent MediaExtractor permission denial
     * or closed file descriptor SIGSEGV crashes on Android 10+.
     */
    suspend fun decodeFromUri(context: Context, uri: Uri): AudioTrackData = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context

        // 1. Resolve human-readable track name
        var title = "Audio Track"
        try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrBlank()) {
                        title = rawName
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Stream URI to a safe temporary file in cacheDir
        val tempFile = File(appContext.cacheDir, "input_audio_${System.currentTimeMillis()}_${(1000..9999).random()}.tmp")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalArgumentException("No se pudo leer el archivo de audio seleccionado.")

            if (tempFile.length() == 0L) {
                throw IllegalArgumentException("El archivo seleccionado está vacío.")
            }

            // 3. First attempt direct fast WAV decoding (standard for music production stems & masters)
            val wavResult = tryDecodeWav(tempFile, title)
            if (wavResult != null && wavResult.samples.isNotEmpty()) {
                return@withContext wavResult
            }

            // 4. Decode compressed formats (MP3, AAC, M4A, FLAC, OGG, OPUS) via MediaCodec
            return@withContext decodeWithMediaCodec(tempFile, title)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Direct RIFF/WAVE parser supporting 16-bit PCM, 24-bit PCM, and 32-bit IEEE float.
     * Bypasses MediaCodec entirely for maximum speed, zero jitter, and absolute format compatibility.
     */
    private fun tryDecodeWav(file: File, displayTitle: String): AudioTrackData? {
        if (file.length() < 44) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(12)
                raf.readFully(header)
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                if (riff != "RIFF" || wave != "WAVE") return null

                var channels = 2
                var sampleRate = 44100
                var bitsPerSample = 16
                var audioFormat = 1 // 1 = PCM, 3 = IEEE Float
                var dataOffset = -1L
                var dataSize = 0L

                while (raf.filePointer < raf.length() - 8) {
                    val chunkId = ByteArray(4)
                    raf.readFully(chunkId)
                    val chunkSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
                    val nextChunk = raf.filePointer + chunkSize
                    val chunkStr = String(chunkId)

                    if (chunkStr == "fmt ") {
                        audioFormat = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                        channels = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                        sampleRate = Integer.reverseBytes(raf.readInt())
                        raf.skipBytes(6) // byteRate (4), blockAlign (2)
                        bitsPerSample = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                    } else if (chunkStr == "data") {
                        dataOffset = raf.filePointer
                        dataSize = chunkSize
                        break
                    }
                    raf.seek(nextChunk)
                }

                if (dataOffset < 0 || sampleRate <= 0 || channels <= 0) return null
                raf.seek(dataOffset)

                val bytesPerFrame = channels * (bitsPerSample / 8)
                if (bytesPerFrame <= 0) return null

                val maxFrames = sampleRate * MAX_TRACK_DURATION_SECONDS
                val availableFrames = (minOf(dataSize, raf.length() - dataOffset) / bytesPerFrame).toInt()
                val framesToRead = minOf(availableFrames, maxFrames)
                if (framesToRead <= 0) return null

                val totalSamples = framesToRead * channels
                val samples = FloatArray(totalSamples)
                val channelCount = channels.coerceIn(1, 2)

                when {
                    // Standard 16-bit PCM WAV
                    audioFormat == 1 && bitsPerSample == 16 -> {
                        val bufferSize = minOf(65536, framesToRead * bytesPerFrame)
                        val byteBuf = ByteArray(bufferSize)
                        val bb = ByteBuffer.wrap(byteBuf).order(ByteOrder.LITTLE_ENDIAN)
                        var sampleIdx = 0
                        var remainingBytes = framesToRead * bytesPerFrame
                        while (remainingBytes > 0 && sampleIdx < totalSamples) {
                            val toRead = minOf(byteBuf.size, remainingBytes)
                            raf.readFully(byteBuf, 0, toRead)
                            bb.position(0)
                            val shortsToRead = toRead / 2
                            for (s in 0 until shortsToRead) {
                                if (sampleIdx < totalSamples) {
                                    samples[sampleIdx++] = bb.short / 32768.0f
                                }
                            }
                            remainingBytes -= toRead
                        }
                    }
                    // 24-bit Studio PCM WAV
                    audioFormat == 1 && bitsPerSample == 24 -> {
                        val bufferSize = minOf(65535, framesToRead * bytesPerFrame)
                        val byteBuf = ByteArray(bufferSize)
                        var sampleIdx = 0
                        var remainingBytes = framesToRead * bytesPerFrame
                        while (remainingBytes > 0 && sampleIdx < totalSamples) {
                            val toRead = minOf(byteBuf.size, remainingBytes)
                            raf.readFully(byteBuf, 0, toRead)
                            var p = 0
                            while (p + 2 < toRead && sampleIdx < totalSamples) {
                                val b0 = byteBuf[p].toInt() and 0xFF
                                val b1 = byteBuf[p + 1].toInt() and 0xFF
                                val b2 = byteBuf[p + 2].toInt() // sign-extended byte
                                val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                                samples[sampleIdx++] = sample24 / 8388608.0f
                                p += 3
                            }
                            remainingBytes -= toRead
                        }
                    }
                    // 32-bit IEEE Float WAV
                    audioFormat == 3 && bitsPerSample == 32 -> {
                        val bufferSize = minOf(65536, framesToRead * bytesPerFrame)
                        val byteBuf = ByteArray(bufferSize)
                        val bb = ByteBuffer.wrap(byteBuf).order(ByteOrder.LITTLE_ENDIAN)
                        var sampleIdx = 0
                        var remainingBytes = framesToRead * bytesPerFrame
                        while (remainingBytes > 0 && sampleIdx < totalSamples) {
                            val toRead = minOf(byteBuf.size, remainingBytes)
                            raf.readFully(byteBuf, 0, toRead)
                            bb.position(0)
                            val floatsToRead = toRead / 4
                            for (f in 0 until floatsToRead) {
                                if (sampleIdx < totalSamples) {
                                    samples[sampleIdx++] = bb.float.coerceIn(-2.0f, 2.0f)
                                }
                            }
                            remainingBytes -= toRead
                        }
                    }
                    else -> return null
                }

                AudioTrackData(
                    title = displayTitle,
                    sampleRate = sampleRate,
                    channels = channelCount,
                    samples = samples
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes compressed audio files via MediaCodec using native primitive buffers.
     */
    private fun decodeWithMediaCodec(file: File, displayTitle: String): AudioTrackData {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            throw IllegalArgumentException("No se pudo iniciar el extractor de audio: ${e.message}")
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
            throw IllegalArgumentException("El archivo no contiene pistas de audio válidas.")
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val codec: MediaCodec
        try {
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            extractor.release()
            throw IllegalArgumentException("No se encontró decodificador para el formato '$mime': ${e.message}")
        }

        val buffer = PrimitiveFloatBuffer(131072)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 5000L
        val maxSamples = sampleRate * channelCount * MAX_TRACK_DURATION_SECONDS
        var noOutputCycles = 0

        try {
            while (!sawOutputEOS && buffer.totalCount < maxSamples) {
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
                    noOutputCycles = 0
                    val outputBuf = codec.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null && bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuf.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuf = outputBuf.asShortBuffer()
                        val count = shortBuf.remaining()
                        for (k in 0 until count) {
                            val s = shortBuf.get()
                            buffer.add(s / 32768.0f)
                        }
                    }
                    codec.releaseOutputBuffer(outputBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (sawInputEOS) {
                        noOutputCycles++
                        if (noOutputCycles > 40) {
                            sawOutputEOS = true
                        }
                    }
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {}
            try {
                codec.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }

        val samples = buffer.toFloatArray()
        if (samples.isEmpty()) {
            throw IllegalArgumentException("No se pudieron extraer datos de audio PCM del archivo.")
        }

        return AudioTrackData(
            title = displayTitle,
            sampleRate = sampleRate,
            channels = channelCount.coerceIn(1, 2),
            samples = samples
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
            val pitch = 50.0 + 110.0 * exp(-35.0 * t)
            val amp = exp(-12.0 * t).toFloat()
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
            samples[frame * 2 + 1] += sample * 1.05f
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
