package com.example.dsp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.AudioTrackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-fidelity 24-bit PCM WAV exporter.
 * Writes standard RIFF WAV files directly to internal/external storage and MediaStore.
 */
object WavWriter {

    suspend fun exportToWav(
        context: Context,
        audioTrack: AudioTrackData,
        customFileName: String? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = customFileName ?: "${audioTrack.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_Master.wav"
            val sampleRate = audioTrack.sampleRate
            val channels = audioTrack.channels
            val samples = audioTrack.samples
            // Export the mastered signal as 24-bit PCM. This is a common
            // delivery format and preserves more quantization resolution than 16-bit.
            val bitsPerSample = 24
            val bytesPerSample = bitsPerSample / 8
            val byteRate = sampleRate * channels * bytesPerSample
            val blockAlign = channels * bytesPerSample
            val dataSize = samples.size * bytesPerSample
            val totalFileSize = 36 + dataSize

            var targetUri: Uri? = null
            val outputStream: OutputStream

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ReggaetonMasterDSP")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry for audio export")
                targetUri = uri
                outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open output stream for export")
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ReggaetonMasterDSP")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                targetUri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            outputStream.use { stream ->
                // Write 44-byte standard RIFF WAV header
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(totalFileSize)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16) // Subchunk1Size for PCM
                header.putShort(1.toShort()) // AudioFormat 1 = PCM
                header.putShort(channels.toShort())
                header.putInt(sampleRate)
                header.putInt(byteRate)
                header.putShort(blockAlign.toShort())
                header.putShort(bitsPerSample.toShort())
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(dataSize)

                stream.write(header.array())

                // Convert 32-bit float processing samples to 24-bit PCM with TPDF dither
                val chunkBufferSize = 4096
                val pcmBuffer = ByteBuffer.allocate(chunkBufferSize * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
                val rnd = java.util.Random()

                var i = 0
                while (i < samples.size) {
                    pcmBuffer.clear()
                    val toProcess = minOf(chunkBufferSize, samples.size - i)
                    for (k in 0 until toProcess) {
                        val sample = samples[i + k].coerceIn(-1.0f, 1.0f)
                        // TPDF dither in units of one 24-bit quantization step.
                        val ditherLsb = rnd.nextFloat() - rnd.nextFloat()
                        val scaled = sample.toDouble() * 8388607.0 + ditherLsb.toDouble()
                        val intSample = kotlin.math.round(scaled).toInt().coerceIn(-8388608, 8388607)
                        // Signed 24-bit PCM, little-endian two's complement.
                        pcmBuffer.put((intSample and 0xFF).toByte())
                        pcmBuffer.put((intSample shr 8 and 0xFF).toByte())
                        pcmBuffer.put((intSample shr 16 and 0xFF).toByte())
                    }
                    stream.write(pcmBuffer.array(), 0, toProcess * bytesPerSample)
                    i += toProcess
                }
                stream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetUri != null) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(targetUri, updateValues, null, null)
            }

            targetUri ?: throw IllegalStateException("Failed to retrieve final exported file URI")
        }
    }
}
