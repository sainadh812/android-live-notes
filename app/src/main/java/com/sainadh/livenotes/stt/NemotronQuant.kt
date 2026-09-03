package com.sainadh.livenotes.stt

enum class NemotronQuant(
    val displayName: String,
    val fileName: String,
    val huggingFaceUrl: String,
    val sizeLabel: String
) {
    Q4_K_M(
        displayName = "Q4_K_M · 473 MB",
        fileName = "nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf",
        huggingFaceUrl = "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf",
        sizeLabel = "473 MB"
    ),
    Q5_K_M(
        displayName = "Q5_K_M · 534 MB",
        fileName = "nemotron-3.5-asr-streaming-0.6b-Q5_K_M.gguf",
        huggingFaceUrl = "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-Q5_K_M.gguf",
        sizeLabel = "534 MB"
    ),
    Q6_K(
        displayName = "Q6_K · 593 MB",
        fileName = "nemotron-3.5-asr-streaming-0.6b-Q6_K.gguf",
        huggingFaceUrl = "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-Q6_K.gguf",
        sizeLabel = "593 MB"
    ),
    Q8_0(
        displayName = "Q8_0 · 716 MB",
        fileName = "nemotron-3.5-asr-streaming-0.6b-Q8_0.gguf",
        huggingFaceUrl = "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-Q8_0.gguf",
        sizeLabel = "716 MB"
    ),
    F16(
        displayName = "F16 · 1.19 GB",
        fileName = "nemotron-3.5-asr-streaming-0.6b-F16.gguf",
        huggingFaceUrl = "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-F16.gguf",
        sizeLabel = "1.19 GB"
    ),
    F32(
        displayName = "F32 · 2.38 GB",
        fileName = "nemotron-3.5-asr-streaming-0.6b-F32.gguf",
        huggingFaceUrl = "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-F32.gguf",
        sizeLabel = "2.38 GB"
    );

    companion object {
        fun fromFileName(fileName: String): NemotronQuant? = entries.firstOrNull { it.fileName == fileName }
    }
}
