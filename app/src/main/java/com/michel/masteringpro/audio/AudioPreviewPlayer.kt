package com.michel.masteringpro.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.michel.masteringpro.model.AudioTrackData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Streaming in-memory PCM audition with immediate A/B source switching. */
class AudioPreviewPlayer(
    private val onFinished: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile private var selectedMaster = false
    @Volatile private var playing = false
    @Volatile private var generation = 0
    private var sourceA: AudioTrackData? = null
    private var sourceB: AudioTrackData? = null
    private var audioTrack: AudioTrack? = null
    private var job: Job? = null

    @Synchronized
    fun play(original: AudioTrackData, mastered: AudioTrackData, useMaster: Boolean) {
        stop()
        sourceA = original
        sourceB = mastered
        selectedMaster = useMaster
        val sessionGeneration = generation
        val channelMask = if (original.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBytes = AudioTrack.getMinBufferSize(original.sampleRate, channelMask, AudioFormat.ENCODING_PCM_FLOAT)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(original.sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes((minBytes.coerceAtLeast(4_096) * 2))
            .build()
        audioTrack = audio
        playing = true
        audio.play()
        job = scope.launch {
            var frame = 0
            val frames = maxOf(original.frameCount, mastered.frameCount)
            try {
                while (isActive && frame < frames) {
                    if (!playing) {
                        delay(20)
                        continue
                    }
                    val current = if (selectedMaster) sourceB ?: break else sourceA ?: break
                    if (frame >= current.frameCount) break
                    val count = minOf(2_048, current.frameCount - frame)
                    val offset = frame * current.channels
                    val written = audio.write(current.samples, offset, count * current.channels, AudioTrack.WRITE_BLOCKING)
                    if (written > 0) frame += written / current.channels else delay(5)
                }
            } catch (_: IllegalStateException) {
                // AudioTrack can be released while a track is being replaced.
            } finally {
                runCatching { audio.pause() }
                runCatching { audio.flush() }
                runCatching { audio.stop() }
                runCatching { audio.release() }
                if (generation == sessionGeneration) {
                    playing = false
                    if (audioTrack === audio) audioTrack = null
                    onFinished()
                }
            }
        }
    }

    fun selectMaster(master: Boolean) {
        selectedMaster = master
        val audio = audioTrack ?: return
        runCatching {
            audio.pause()
            audio.flush()
            if (playing) audio.play()
        }
    }

    fun hasSession(): Boolean = audioTrack != null

    fun pause() {
        playing = false
        runCatching { audioTrack?.pause() }
    }

    fun resume() {
        if (audioTrack == null) return
        playing = true
        runCatching { audioTrack?.play() }
    }

    @Synchronized
    fun stop() {
        generation++
        playing = false
        job?.cancel()
        job = null
        val audio = audioTrack
        audioTrack = null
        runCatching { audio?.pause() }
        runCatching { audio?.flush() }
        runCatching { audio?.stop() }
        runCatching { audio?.release() }
        sourceA = null
        sourceB = null
    }

    fun close() {
        stop()
        scope.cancel()
    }
}
