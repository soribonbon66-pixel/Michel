package com.michel.masteringpro.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.michel.masteringpro.model.AudioTrackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes an IEEE-float 32-bit WAV to Music/Michel (MediaStore on Android 10+, app Music folder on older). */
object WavWriter {
    data class ExportedAudio(val uri: Uri, val fileName: String)

    suspend fun export(context: Context, track: AudioTrackData): ExportedAudio = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "michel_mastered_$stamp.wav"
        val dataBytes = track.samples.size.toLong() * 4L
        require(dataBytes <= Int.MAX_VALUE - 36L) { "El WAV supera el límite RIFF de 4 GB" }
        var targetUri: Uri? = null
        var outputStream: OutputStream? = null
        var pendingMediaStore = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Michel")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                targetUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear el archivo en Música/Michel")
                pendingMediaStore = true
                outputStream = context.contentResolver.openOutputStream(targetUri!!, "w")
                    ?: throw IllegalStateException("No se pudo abrir el WAV para escritura")
            } else {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
                val directory = File(base, "Michel")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("No se pudo crear la carpeta Music/Michel")
                val file = File(directory, fileName)
                outputStream = FileOutputStream(file)
                targetUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            outputStream.use { stream ->
                writeHeader(stream, track.sampleRate, track.channels, dataBytes.toInt())
                val byteBuffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                for (sample in track.samples) {
                    if (byteBuffer.remaining() < 4) {
                        stream.write(byteBuffer.array(), 0, byteBuffer.position())
                        byteBuffer.clear()
                    }
                    byteBuffer.putFloat(sample.coerceIn(-1f, 1f))
                }
                if (byteBuffer.position() > 0) stream.write(byteBuffer.array(), 0, byteBuffer.position())
                stream.flush()
            }
            outputStream = null

            if (pendingMediaStore && targetUri != null) {
                val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                context.contentResolver.update(targetUri!!, values, null, null)
                pendingMediaStore = false
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
                MediaScannerConnection.scanFile(context, arrayOf(File(File(base, "Michel"), fileName).absolutePath), arrayOf("audio/wav"), null)
            }
            ExportedAudio(targetUri ?: throw IllegalStateException("No se generó URI para el WAV"), fileName)
        } catch (throwable: Throwable) {
            runCatching { outputStream?.close() }
            if (pendingMediaStore && targetUri != null) runCatching { context.contentResolver.delete(targetUri!!, null, null) }
            throw throwable
        }
    }

    private fun writeHeader(stream: OutputStream, sampleRate: Int, channels: Int, dataSize: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM/IEEE-float fmt chunk
        header.putShort(3.toShort()) // WAVE_FORMAT_IEEE_FLOAT
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 4)
        header.putShort((channels * 4).toShort())
        header.putShort(32.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        stream.write(header.array())
    }
}
