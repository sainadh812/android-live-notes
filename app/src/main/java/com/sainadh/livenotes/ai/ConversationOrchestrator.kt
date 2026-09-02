package com.sainadh.livenotes.ai

import com.sainadh.livenotes.data.ApiKeyStore
import com.sainadh.livenotes.data.DailyNote
import com.sainadh.livenotes.data.NotesRepository
import com.sainadh.livenotes.data.TranscriptChunk
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConversationOrchestrator(
    private val repository: NotesRepository,
    private val apiKeyStore: ApiKeyStore,
    private val chatCompletionClient: ChatCompletionClient
) {
    private val summarizeMutex = Mutex()

    suspend fun onTranscript(text: String, isFinal: Boolean, timestampMs: Long = System.currentTimeMillis()) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return

        val dateKey = repository.todayKey()
        val previousChunk = repository.latestChunk(dateKey)
        repository.appendTranscript(dateKey, cleaned, isFinal, timestampMs)

        if (!shouldSummarize(cleaned, isFinal, timestampMs, previousChunk)) return

        summarizeMutex.withLock {
            val apiKey = apiKeyStore.readApiKey()
            if (apiKey.isBlank()) return

            val provider = apiKeyStore.readProvider()
            val model = apiKeyStore.readModel(provider)
            val existing = repository.getNote(dateKey)
            val transcriptWindow = repository.recentTranscript(dateKey, limit = 12)
                .joinToString(separator = "\n") { chunk ->
                    val prefix = if (chunk.isFinal) "final" else "partial"
                    "[$prefix] ${chunk.text}"
                }
            if (transcriptWindow.isBlank()) return

            val result = chatCompletionClient.summarizeConversation(
                LlmSummaryRequest(
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    priorSummary = existing?.summary.orEmpty(),
                    runningContext = existing?.runningContext.orEmpty(),
                    recentTranscript = transcriptWindow
                )
            ).getOrElse { return }

            repository.upsertNote(
                DailyNote(
                    dateKey = dateKey,
                    summary = result.summary,
                    runningContext = result.runningContext,
                    actionItems = result.actionItems,
                    updatedAtEpochMs = timestampMs
                )
            )
        }
    }

    private fun shouldSummarize(
        latestText: String,
        isFinal: Boolean,
        nowMs: Long,
        previousChunk: TranscriptChunk?
    ): Boolean {
        if (isFinal) return true
        if (latestText.length > 160) return true
        val lastChunk = previousChunk ?: return true
        return nowMs - lastChunk.createdAtEpochMs > 30_000L
    }
}
