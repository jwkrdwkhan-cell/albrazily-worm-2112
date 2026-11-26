package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class UserWorkspace(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val trainingLocked: Boolean = false,
    val trainingDays: Long = 7L,
    val trainingUnlockAtMillis: Long = 0L,
    val trainingStatus: String = "idle",
    val chromeVaultToken: String? = null,
    val pinnedContextAnchor: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val contextSummary: String = "",
    val messageCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val codeBlock: String? = null,
    val codeLanguage: String? = null,
    val deepSearchUsed: Boolean = false,
    val boosthisVerified: Boolean = false,
    val emotionTag: String? = null,
    val sources: List<String>? = null,
    val attachmentNames: List<String>? = null,
    val hungerTxtFilename: String? = null,
    val hungerTxtContent: String? = null,
    val marketingPromptPending: Boolean = false,
    val marketingProjectContext: String? = null,
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TrainingBook(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val mimeType: String = "text/plain",
    val totalChunks: Long = 1L,
    val processedChunks: Long = 0L,
    val qaCount: Long = 0L,
    val trainingStatus: String = "queued",
    val summary: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class KnowledgeEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val question: String = "",
    val scenario: String = "",
    val expertAnswer: String = "",
    val keywords: List<String>? = null,
    val sourceType: String = "book_training",
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HungerReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topic: String = "",
    val filename: String = "",
    val reportContent: String = "",
    val learnedIntoVault: Boolean = false,
    val createdAt: Timestamp? = null
)

enum class AttachmentKind {
    IMAGE,
    FILE,
    CAMERA_PHOTO
}

data class PendingAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
    val extractedText: String? = null,
    val sizeBytes: Long = 0L
)

enum class EmotionState(val labelAr: String, val badgeColorHex: Long) {
    EXCITED_CONFIDENT("حماس وثقة عالية", 0xFFF5C451),
    STRESSED_ANXIOUS("توتر وقلق — نبرة مريحة", 0xFF2CE6FF),
    ANGRY_FRUSTRATED("غضب — تهدئة واحتواء", 0xFF2CE59B),
    CALM_NEUTRAL("هدوء وتركيز", 0xFFA4B0C6)
}

data class WebSearchSource(
    val title: String,
    val url: String,
    val snippet: String,
    val verified: Boolean
)

data class BoosthisAuditResult(
    val passed: Boolean,
    val linesChecked: Int,
    val fixedIssues: List<String>,
    val telemetryActive: Boolean
)

data class QueuedBookFile(
    val id: String,
    val title: String,
    val mimeType: String,
    val fullText: String,
    val chunks: List<String>
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
