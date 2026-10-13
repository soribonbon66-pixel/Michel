package com.michel.masteringpro.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.michel.masteringpro.model.AudioTrackData
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt

/** Copies a selected document to cacheDir, decodes it with MediaExtractor/MediaCodec and normalizes PCM. */
object AudioLoader {
    fun load(context: Context, uri: Uri): AudioTrackData {
        val displayName = queryDisplayName(context, uri) ?: "audio"
        val cached = copyIntoCache(context, uri, displayName)
        return try {
            val decoded = decodeFile(cached, displayName)
            toSupportedPcm(decoded.first, decoded.second, decoded.third, displayName)
        } finally {
            cached.delete()
        }
    }

    private fun copyIntoCache(context: Context, uri: Uri, displayName: String): File {
        val suffix = displayName.substringAfterLast('.', "audio").take(8).let { ".$it" }
        val target = File.createTempFile("michel_track_", suffix, context.cacheDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado")
        input.use { source ->
            FileOutputStream(target).use { destination -> source.copyTo(destination, 64 * 1024) }
        }
        return target
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** Returns decoded interleaved PCM, sample rate and channel count. */
    private fun decodeFile(file: File, displayName: String): Triple<FloatArray, Int, Int> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val pcm = FloatAccumulator()
        var outputRate = 0
        var outputChannels = 0
        try {
            extractor.setDataSource(file.absolutePath)
            var selectedFormat: MediaFormat? = null
            var selectedMime: String? = null
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    selectedFormat = format
                    selectedMime = mime
                    extractor.selectTrack(trackIndex)
                    break
                }
            }
            val format = selectedFormat ?: throw IllegalArgumentException("No se encontró una pista de audio en $displayName")
            val mime = selectedMime ?: throw IllegalArgumentException("Formato de audio no reconocido")
            outputRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48_000
            outputChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            if (mime == "audio/raw") {
                val encoding = if (Build.VERSION.SDK_INT >= 24 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
                val directPcm = decodeRawExtractor(extractor, encoding)
                if (directPcm.size == 0) throw IllegalArgumentException("El WAV no contiene datos de audio")
                return Triple(directPcm, outputRate, outputChannels)
            }

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val expectedSamples = if (durationUs > 0L) {
                val expected = durationUs.toDouble() / 1_000_000.0 * outputRate * outputChannels
                expected.toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else 0
            pcm.ensureCapacity(expectedSamples)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var idleCycles = 0

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("El decoder no entregó un buffer de entrada")
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, size, presentationTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleCycles++
                        if (inputEnded && idleCycles > 100) throw IllegalStateException("El decoder de audio dejó de responder")
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decodedFormat = codec.outputFormat
                        if (decodedFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (decodedFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputChannels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        idleCycles = 0
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        idleCycles = 0
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            val outputFormat = codec.outputFormat
                            if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val encoding = if (Build.VERSION.SDK_INT >= 24 && outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            appendPcm(outputBuffer, info, encoding, pcm)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
        if (pcm.size == 0) throw IllegalArgumentException("No se pudo decodificar audio desde $displayName")
        if (outputRate <= 0 || outputChannels <= 0) throw IllegalArgumentException("El stream no informó frecuencia o canales válidos")
        return Triple(pcm.toArray(), outputRate, outputChannels)
    }

    private fun decodeRawExtractor(extractor: MediaExtractor, encoding: Int): FloatArray {
        val output = FloatAccumulator()
        var buffer = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = extractor.sampleSize
            if (sampleSize < 0L) break
            require(sampleSize <= Int.MAX_VALUE.toLong()) { "El bloque PCM es demasiado grande" }
            val required = sampleSize.toInt()
            if (required > buffer.capacity()) {
                buffer = ByteBuffer.allocateDirect(required).order(ByteOrder.LITTLE_ENDIAN)
            }
            buffer.clear()
            val bytesRead = extractor.readSampleData(buffer, 0)
            if (bytesRead < 0) break
            if (bytesRead > 0) {
                buffer.position(0)
                info.offset = 0
                info.size = bytesRead
                appendPcm(buffer, info, encoding, output)
            }
            extractor.advance()
        }
        return output.toArray()
    }

    private fun appendPcm(buffer: ByteBuffer, info: MediaCodec.BufferInfo, encoding: Int, output: FloatAccumulator) {
        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(info.offset)
        duplicate.limit(info.offset + info.size)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> 2
        }
        val readableBytes = duplicate.remaining() - duplicate.remaining() % bytesPerSample
        val end = duplicate.position() + readableBytes
        while (duplicate.position() < end) {
            val value = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> duplicate.float
                AudioFormat.ENCODING_PCM_8BIT -> (((duplicate.get().toInt() and 0xff) - 128) / 128f)
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val b0 = duplicate.get().toInt() and 0xff
                    val b1 = duplicate.get().toInt() and 0xff
                    val b2 = duplicate.get().toInt()
                    val signed = (b2 shl 16) or (b1 shl 8) or b0
                    (if (signed and 0x800000 != 0) signed or -0x1000000 else signed) / 8_388_608f
                }
                AudioFormat.ENCODING_PCM_32BIT -> duplicate.int / 2_147_483_648f
                else -> duplicate.short / 32_768f
            }
            output.append(if (value.isFinite()) value else 0f)
        }
    }

    private fun toSupportedPcm(raw: FloatArray, sourceRate: Int, sourceChannels: Int, title: String): AudioTrackData {
        if (sourceChannels <= 0) throw IllegalArgumentException("Número de canales no válido")
        val usableChannels = sourceChannels.coerceAtMost(8)
        val sourceFrames = raw.size / sourceChannels
        if (sourceFrames <= 0) throw IllegalArgumentException("La pista no contiene frames de audio")
        val outputRate = if (sourceRate == 44_100 || sourceRate == 48_000) sourceRate else 48_000
        val outputChannels = if (sourceChannels == 1) 1 else 2
        if (sourceRate == outputRate && sourceChannels <= 2) {
            return AudioTrackData(title = title, sampleRate = outputRate, channels = outputChannels, samples = raw)
        }
        val outputFramesLong = (sourceFrames.toLong() * outputRate / sourceRate).coerceAtLeast(1L)
        require(outputFramesLong <= Int.MAX_VALUE / outputChannels) { "El track es demasiado largo para procesarlo en memoria" }
        val output = FloatArray(outputFramesLong.toInt() * outputChannels)

        fun channel(frame: Int, channel: Int): Float = raw[(frame.coerceIn(0, sourceFrames - 1) * sourceChannels) + channel]
        fun downmix(frame: Int, left: Boolean): Float {
            if (sourceChannels == 1) return channel(frame, 0)
            var sum = channel(frame, if (left) 0 else 1).toDouble()
            var weight = 1.0
            if (usableChannels > 2) {
                sum += channel(frame, 2) * 0.70710678
                weight += 0.70710678
            }
            if (usableChannels > 3) {
                sum += channel(frame, 3) * 0.5
                weight += 0.5
            }
            if (usableChannels > 4 && left) {
                sum += channel(frame, 4) * 0.70710678
                weight += 0.70710678
            }
            if (usableChannels > 5 && !left) {
                sum += channel(frame, 5) * 0.70710678
                weight += 0.70710678
            }
            return (sum / weight).toFloat()
        }

        for (outputFrame in 0 until outputFramesLong.toInt()) {
            val sourcePosition = outputFrame.toDouble() * sourceRate / outputRate
            val sourceIndex = floor(sourcePosition).toInt().coerceAtMost(sourceFrames - 1)
            val nextIndex = (sourceIndex + 1).coerceAtMost(sourceFrames - 1)
            val fraction = (sourcePosition - sourceIndex).toFloat()
            for (channelIndex in 0 until outputChannels) {
                val first = downmix(sourceIndex, channelIndex == 0)
                val second = downmix(nextIndex, channelIndex == 0)
                output[outputFrame * outputChannels + channelIndex] = (first + (second - first) * fraction).coerceIn(-8f, 8f)
            }
        }
        return AudioTrackData(title = title, sampleRate = outputRate, channels = outputChannels, samples = output)
    }

    private class FloatAccumulator(initialCapacity: Int = 8_192) {
        private var values = FloatArray(initialCapacity.coerceAtLeast(1))
        var size: Int = 0
            private set

        fun ensureCapacity(capacity: Int) {
            if (capacity > values.size) values = values.copyOf(capacity)
        }

        fun append(value: Float) {
            if (size == values.size) values = values.copyOf((values.size * 1.5f).roundToInt().coerceAtLeast(values.size + 1))
            values[size++] = value
        }

        fun toArray(): FloatArray = if (size == values.size) values else values.copyOf(size)
    }
}
