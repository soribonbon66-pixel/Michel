package com.example.dsp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AudioTrackData
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Low-latency real-time PCM player with instantaneous A/B Bypass switching.
 * Plays directly via AudioTrack and streams audio position & live metering to the UI.
 */
class RealtimeAudioPlayer(
    private val onPlaybackPosition: (currentSec: Float, totalSec: Float, progress: Float) -> Unit,
    private val onMeterUpdate: (peakL: Float, peakR: Float) -> Unit,
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit
) {
    @Volatile
    private var rawTrack: AudioTrackData? = null
    @Volatile
    private var masteredTrack: AudioTrackData? = null

    @Volatile
    var isBypassActive: Boolean = false // true = Original Raw (A), false = Mastered (B)

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var isLooping: Boolean = true

    @Volatile
    private var currentFrameIndex = 0

    private val audioLock = Any()
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("RealtimeAudioPlayer", "Audio player caught exception", throwable)
    }
    private val playerScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    fun setTracks(raw: AudioTrackData, mastered: AudioTrackData?) {
        this.rawTrack = raw
        this.masteredTrack = mastered
    }

    fun updateMasteredTrack(mastered: AudioTrackData) {
        this.masteredTrack = mastered
    }

    fun play() {
        val raw = rawTrack ?: return
        if (isPlaying) return

        val sampleRate = raw.sampleRate
        val channels = raw.channels
        val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeInBytes = max(minBufferSize * 2, 4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true
        onPlaybackStateChanged(true)

        playbackJob = playerScope.launch {
            try {
                val shortChunkSize = 2048
                val byteBuffer = ByteBuffer.allocate(shortChunkSize * 2).order(ByteOrder.LITTLE_ENDIAN)

                while (isActive && isPlaying) {
                    val currentRaw = rawTrack ?: break
                    val currentMastered = masteredTrack
                    val activeTrack = if (isBypassActive || currentMastered == null) currentRaw else currentMastered
                    val samples = activeTrack.samples
                    val trackFrames = samples.size / channels

                    byteBuffer.clear()
                    var peakL = 0.0f
                    var peakR = 0.0f

                    var samplesWritten = 0
                    while (samplesWritten < shortChunkSize && currentFrameIndex < trackFrames) {
                        val frameIdx = currentFrameIndex * channels
                        val l = samples.getOrElse(frameIdx) { 0f }.coerceIn(-1.0f, 1.0f)
                        val r = if (channels > 1) {
                            samples.getOrElse(frameIdx + 1) { 0f }.coerceIn(-1.0f, 1.0f)
                        } else l

                        val absL = abs(l)
                        val absR = abs(r)
                        if (absL > peakL) peakL = absL
                        if (absR > peakR) peakR = absR

                        byteBuffer.putShort((l * 32767.0f).toInt().toShort())
                        if (channels > 1) {
                            byteBuffer.putShort((r * 32767.0f).toInt().toShort())
                            samplesWritten += 2
                        } else {
                            samplesWritten += 1
                        }

                        currentFrameIndex++
                    }

                    if (byteBuffer.position() > 0 && isPlaying) {
                        synchronized(audioLock) {
                            val track = audioTrack
                            if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                track.write(byteBuffer.array(), 0, byteBuffer.position())
                            }
                        }
                    }

                    val currentSec = currentFrameIndex.toFloat() / sampleRate
                    val totalSec = trackFrames.toFloat() / sampleRate
                    val progress = if (trackFrames > 0) currentFrameIndex.toFloat() / trackFrames else 0f
                    onPlaybackPosition(currentSec, totalSec, progress)
                    onMeterUpdate(peakL, peakR)

                    if (currentFrameIndex >= trackFrames) {
                        if (isLooping) {
                            currentFrameIndex = 0
                        } else {
                            pause()
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    android.util.Log.e("RealtimeAudioPlayer", "Playback loop error", t)
                }
            }
        }
    }

    fun pause() {
        isPlaying = false
        onPlaybackStateChanged(false)
        playbackJob?.cancel()
        playbackJob = null
        synchronized(audioLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
        }
        onMeterUpdate(0f, 0f)
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(progress: Float) {
        val raw = rawTrack ?: return
        val totalFrames = raw.samples.size / raw.channels
        currentFrameIndex = (progress.coerceIn(0f, 1f) * totalFrames).toInt()
        val currentSec = currentFrameIndex.toFloat() / raw.sampleRate
        val totalSec = totalFrames.toFloat() / raw.sampleRate
        onPlaybackPosition(currentSec, totalSec, progress)
    }

    fun release() {
        pause()
        playerScope.cancel()
    }
}
