package com.sainadh.livenotes.stt

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState

    data class Downloading(
        val quant: NemotronQuant,
        val bytesDownloaded: Long,
        val totalBytes: Long?
    ) : ModelDownloadState {
        val progressFraction: Float?
            get() = totalBytes?.takeIf { it > 0L }?.let { bytesDownloaded.toFloat() / it.toFloat() }
    }

    data class Ready(
        val quant: NemotronQuant,
        val absolutePath: String,
        val fileSizeBytes: Long
    ) : ModelDownloadState

    data class Error(val message: String) : ModelDownloadState
}

data class DownloadedModel(
    val quant: NemotronQuant,
    val file: File
)

class ModelDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _modelDownloadState = MutableStateFlow(currentStateFromDisk())
    val modelDownloadState: StateFlow<ModelDownloadState> = _modelDownloadState.asStateFlow()

    @Volatile
    private var currentDownloadJob: Job? = null

    fun findAnyDownloaded(): DownloadedModel? {
        val modelsDir = modelsDir()
        return NemotronQuant.entries
            .map { quant -> DownloadedModel(quant, File(modelsDir, quant.fileName)) }
            .firstOrNull { it.file.exists() && it.file.length() > 0L }
    }

    fun downloadModel(quant: NemotronQuant) {
        currentDownloadJob?.cancel()
        currentDownloadJob = scope.launch {
            val finalFile = File(modelsDir(), quant.fileName)
            if (finalFile.exists() && finalFile.length() > 0L) {
                _modelDownloadState.value = ModelDownloadState.Ready(
                    quant = quant,
                    absolutePath = finalFile.absolutePath,
                    fileSizeBytes = finalFile.length()
                )
                return@launch
            }

            val tempFile = File(finalFile.parentFile, "${quant.fileName}.part")
            tempFile.parentFile?.mkdirs()
            tempFile.delete()

            try {
                val request = Request.Builder().url(quant.huggingFaceUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Model download failed with HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Model download returned an empty response body")
                    val totalBytes = body.contentLength().takeIf { it > 0L }
                    _modelDownloadState.value = ModelDownloadState.Downloading(
                        quant = quant,
                        bytesDownloaded = 0L,
                        totalBytes = totalBytes
                    )

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                _modelDownloadState.value = ModelDownloadState.Downloading(
                                    quant = quant,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes
                                )
                            }
                            output.flush()
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw IllegalStateException("Downloaded model file is empty")
                }
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!tempFile.renameTo(finalFile)) {
                    throw IllegalStateException("Downloaded model could not be moved into place")
                }
                _modelDownloadState.value = ModelDownloadState.Ready(
                    quant = quant,
                    absolutePath = finalFile.absolutePath,
                    fileSizeBytes = finalFile.length()
                )
            } catch (cancelled: CancellationException) {
                tempFile.delete()
                _modelDownloadState.value = currentStateFromDisk()
                throw cancelled
            } catch (error: Throwable) {
                tempFile.delete()
                _modelDownloadState.value = ModelDownloadState.Error(
                    error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun currentStateFromDisk(): ModelDownloadState {
        val downloaded = findAnyDownloaded() ?: return ModelDownloadState.Idle
        return ModelDownloadState.Ready(
            quant = downloaded.quant,
            absolutePath = downloaded.file.absolutePath,
            fileSizeBytes = downloaded.file.length()
        )
    }

    private fun modelsDir(): File = File(context.filesDir, "models")
}
