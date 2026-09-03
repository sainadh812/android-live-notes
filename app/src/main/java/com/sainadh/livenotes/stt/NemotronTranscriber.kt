package com.sainadh.livenotes.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class NemotronTranscriber(
    private val context: Context,
    private val listener: SpeechTranscriber.Listener,
    private val modelPath: String,
    private val languageTag: String = DEFAULT_LANGUAGE_TAG
) : SpeechTranscriber {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val running = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var streamJob: Job? = null
    private var nativeHandle: Long = 0L
    private var committedTranscript: String = ""
    private var tentativeTranscript: String = ""

    override fun start() {
        if (!running.compareAndSet(false, true)) return

        val bridgeError = NemotronNativeBridge.loadError
        if (bridgeError != null) {
            running.set(false)
            listener.onError("Nemotron native bridge is unavailable in this build: ${bridgeError.message ?: bridgeError.javaClass.simpleName}")
            listener.onStateChanged("stopped")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            running.set(false)
            listener.onError("Microphone permission missing for on-device transcription")
            listener.onStateChanged("stopped")
            return
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            running.set(false)
            listener.onError("Downloaded Nemotron model is missing: ${modelFile.absolutePath}")
            listener.onStateChanged("stopped")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            running.set(false)
            listener.onError("AudioRecord could not allocate a 16kHz mono PCM buffer")
            listener.onStateChanged("stopped")
            return
        }

        try {
            nativeHandle = NemotronNativeBridge.nativeInit(modelFile.absolutePath, languageTag)
            if (nativeHandle == 0L) {
                throw IllegalStateException("nativeInit returned an invalid handle")
            }

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(STREAM_BUFFER_BYTES)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize for Nemotron")
            }

            resetTranscriptState()
            record.startRecording()
            audioRecord = record
            listener.onStateChanged("on-device model listening")

            streamJob = scope.launch {
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                while (running.get()) {
                    val bytesRead = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (bytesRead > 0) {
                        val updateJson = NemotronNativeBridge.nativeFeedPcm(nativeHandle, buffer, bytesRead)
                        emitNativeUpdate(updateJson, isFinal = false)
                    } else if (bytesRead < 0) {
                        listener.onError("AudioRecord read failed for Nemotron: $bytesRead")
                        break
                    }
                }
            }
        } catch (error: Throwable) {
            running.set(false)
            releaseAudioRecord()
            releaseNativeHandle()
            listener.onError(error.message ?: error.javaClass.simpleName)
            listener.onStateChanged("stopped")
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        streamJob?.cancel()
        emitNativeUpdate(runCatching { NemotronNativeBridge.nativeFinalizeStream(nativeHandle) }.getOrDefault(""), isFinal = true)
        runCatching { NemotronNativeBridge.nativeRestartStream(nativeHandle) }
        releaseAudioRecord()
        listener.onStateChanged("stopped")
    }

    override fun destroy() {
        running.set(false)
        streamJob?.cancel()
        releaseAudioRecord()
        releaseNativeHandle()
        scope.cancel()
    }

    private fun emitNativeUpdate(updateJson: String, isFinal: Boolean) {
        if (updateJson.isBlank()) return
        val update = runCatching { json.decodeFromString<NativeTranscriptUpdate>(updateJson) }.getOrNull() ?: return
        if (update.error.isNotBlank()) {
            listener.onError(update.error)
            return
        }
        if (update.committed.isNotBlank()) {
            committedTranscript = update.committed.trim()
        }
        tentativeTranscript = update.tentative.trim()
        val combined = listOf(committedTranscript, tentativeTranscript)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .trim()
        if (combined.isNotBlank()) {
            listener.onTranscript(combined, isFinal && tentativeTranscript.isBlank())
        }
    }

    private fun releaseAudioRecord() {
        val record = audioRecord ?: return
        audioRecord = null
        runCatching { record.stop() }
        record.release()
    }

    private fun releaseNativeHandle() {
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) {
            runCatching { NemotronNativeBridge.nativeRelease(handle) }
        }
    }

    private fun resetTranscriptState() {
        committedTranscript = ""
        tentativeTranscript = ""
    }

    @Serializable
    private data class NativeTranscriptUpdate(
        val committed: String = "",
        val tentative: String = "",
        val error: String = ""
    )

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val STREAM_BUFFER_BYTES = 3_200
        const val DEFAULT_LANGUAGE_TAG = "en-US"
    }
}

private object NemotronNativeBridge {
    val loadError: Throwable? = runCatching {
        System.loadLibrary("nemotron_jni")
    }.exceptionOrNull()

    external fun nativeInit(modelPath: String, languageTag: String): Long
    external fun nativeFeedPcm(handle: Long, pcmBytes: ByteArray, byteCount: Int): String
    external fun nativeFinalizeStream(handle: Long): String
    external fun nativeRestartStream(handle: Long)
    external fun nativeRelease(handle: Long)
}
