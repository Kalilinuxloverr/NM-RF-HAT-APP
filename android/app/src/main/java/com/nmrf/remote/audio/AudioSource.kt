package com.nmrf.remote.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

interface AudioSource {
    val frames: Flow<FloatArray>
    val sampleRate: Int
    val frameSize: Int
}

/** Mikrofon -> Float-Frames (-1..1). Braucht RECORD_AUDIO. */
class MicAudioSource : AudioSource {
    override val sampleRate = 44100
    override val frameSize = 2048

    @SuppressLint("MissingPermission")
    override val frames: Flow<FloatArray> = flow {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(frameSize * 2)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf,
        )
        val buf = ShortArray(frameSize)
        try {
            rec.startRecording()
            while (currentCoroutineContext().isActive) {
                val n = rec.read(buf, 0, frameSize)
                if (n > 0) {
                    emit(FloatArray(frameSize) { if (it < n) buf[it] / 32768f else 0f })
                }
            }
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
    }.flowOn(Dispatchers.IO)
}
