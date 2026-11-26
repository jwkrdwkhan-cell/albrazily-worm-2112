package com.example.firebase

import android.content.Context
import com.example.R
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.HungerReport
import com.example.model.KnowledgeEntry
import com.example.model.TrainingBook
import com.example.model.UserWorkspace
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NexusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext)
    )

    companion object {
        private fun createConfiguredFirestore(appContext: Context): FirebaseFirestore {
            val dbId = appContext.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(dbId)
            try {
                val settings = firestoreSettings {
                    setLocalCacheSettings(
                        persistentCacheSettings {
                            setSizeBytes(100L * 1024L * 1024L)
                        }
                    )
                }
                instance.firestoreSettings = settings
            } catch (_: Exception) {
                // Settings may already be locked if Firestore was initialized earlier
            }
            return instance
        }
    }

    private fun requireAuthenticatedUid(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUid != null && uid != expectedUid) {
            throw IllegalStateException("Signed-in user changed; refusing cross-user operation.")
        }
        return uid
    }

    private fun sanitizeId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank {
            UUID.randomUUID().toString().replace("-", "_")
        }
    }

    private fun unwrapFirestoreCatch(error: Throwable, op: OperationType, path: String, uid: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) {
            throw error
        }
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, op, path)
        }
        return firestoreError ?: error
    }

    fun observeWorkspace(userId: String): Flow<UserWorkspace?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserWorkspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                throw unwrapFirestoreCatch(error, OperationType.GET, path, userId)
            }
    }

    suspend fun ensureWorkspace(userId: String, displayName: String, email: String?): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "Nexus Commander" }.take(80)
                val token = "nxv_${UUID.randomUUID().toString().replace("-", "").take(20)}"
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email?.take(150)?.takeIf { it.isNotBlank() },
                    "trainingLocked" to false,
                    "trainingDays" to 7L,
                    "trainingUnlockAtMillis" to 0L,
                    "trainingStatus" to "idle",
                    "chromeVaultToken" to token,
                    "pinnedContextAnchor" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTrainingLock(
        userId: String,
        locked: Boolean,
        days: Long,
        unlockAtMillis: Long,
        status: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val safeDays = days.coerceIn(1L, 365L)
            val safeStatus = status.takeIf { it in listOf("idle", "training", "locked", "completed") } ?: "idle"
            db.collection("users").document(uid).update(
                mapOf(
                    "trainingLocked" to locked,
                    "trainingDays" to safeDays,
                    "trainingUnlockAtMillis" to unlockAtMillis.coerceAtLeast(0L),
                    "trainingStatus" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePinnedContextAnchor(userId: String, anchor: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            db.collection("users").document(uid).update(
                mapOf(
                    "pinnedContextAnchor" to anchor.take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        val path = "users/$userId/conversations"
        return db.collection("users").document(userId).collection("conversations")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                throw unwrapFirestoreCatch(error, OperationType.LIST, path, userId)
            }
    }

    suspend fun createConversation(userId: String, title: String): Result<String> {
        val path = "users/$userId/conversations"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val convRef = db.collection("users").document(uid).collection("conversations").document()
            val safeTitle = title.trim().ifBlank { "جلسة ذكاء جديدة" }.take(120)
            convRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "contextSummary" to "",
                    "messageCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationMeta(
        userId: String,
        convId: String,
        title: String? = null,
        contextSummary: String? = null,
        incrementMessages: Boolean = false
    ): Result<Unit> {
        val path = "users/$userId/conversations/$convId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (title != null && title.isNotBlank()) {
                updates["title"] = title.trim().take(120)
            }
            if (contextSummary != null) {
                updates["contextSummary"] = contextSummary.take(8000)
            }
            if (incrementMessages) {
                updates["messageCount"] = FieldValue.increment(1L)
            }
            db.collection("users").document(uid).collection("conversations").document(convId)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(userId: String, convId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/conversations/$convId/messages"
        return db.collection("users").document(userId)
            .collection("conversations").document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                throw unwrapFirestoreCatch(error, OperationType.LIST, path, userId)
            }
    }

    suspend fun addMessage(userId: String, convId: String, message: ChatMessage): Result<String> {
        val path = "users/$userId/conversations/$convId/messages"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val msgRef = db.collection("users").document(uid)
                .collection("conversations").document(convId)
                .collection("messages").document()
            val safeRole = message.role.takeIf { it in listOf("user", "model", "system") } ?: "user"
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "role" to safeRole,
                "content" to message.content.take(60000),
                "codeBlock" to message.codeBlock?.take(60000),
                "codeLanguage" to message.codeLanguage?.take(40),
                "deepSearchUsed" to message.deepSearchUsed,
                "boosthisVerified" to message.boosthisVerified,
                "emotionTag" to message.emotionTag?.take(40),
                "sources" to message.sources?.take(20),
                "attachmentNames" to message.attachmentNames?.take(20),
                "hungerTxtFilename" to message.hungerTxtFilename?.take(200),
                "hungerTxtContent" to message.hungerTxtContent?.take(40000),
                "marketingPromptPending" to message.marketingPromptPending,
                "marketingProjectContext" to message.marketingProjectContext?.take(2000),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            msgRef.set(payload).await()
            updateConversationMeta(uid, convId, incrementMessages = true)
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMessageMarketingPrompt(
        userId: String,
        convId: String,
        msgId: String,
        pending: Boolean
    ): Result<Unit> {
        val path = "users/$userId/conversations/$convId/messages/$msgId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            db.collection("users").document(uid)
                .collection("conversations").document(convId)
                .collection("messages").document(msgId)
                .update(mapOf("marketingPromptPending" to pending))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeTrainingBooks(userId: String): Flow<List<TrainingBook>> {
        val path = "users/$userId/training_books"
        return db.collection("users").document(userId).collection("training_books")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TrainingBook::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                throw unwrapFirestoreCatch(error, OperationType.LIST, path, userId)
            }
    }

    suspend fun createTrainingBook(
        userId: String,
        bookId: String,
        title: String,
        mimeType: String,
        totalChunks: Long
    ): Result<String> {
        val safeId = sanitizeId(bookId)
        val path = "users/$userId/training_books/$safeId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val docRef = db.collection("users").document(uid).collection("training_books").document(safeId)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to title.trim().ifBlank { "Untitled Book" }.take(200),
                    "mimeType" to mimeType.ifBlank { "text/plain" }.take(100),
                    "totalChunks" to totalChunks.coerceIn(1L, 5000L),
                    "processedChunks" to 0L,
                    "qaCount" to 0L,
                    "trainingStatus" to "queued",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTrainingBookProgress(
        userId: String,
        bookId: String,
        processedChunks: Long,
        qaCount: Long,
        status: String,
        summary: String? = null
    ): Result<Unit> {
        val safeId = sanitizeId(bookId)
        val path = "users/$userId/training_books/$safeId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val safeStatus = status.takeIf { it in listOf("queued", "reading", "completed", "failed") } ?: "reading"
            val updates = mutableMapOf<String, Any?>(
                "processedChunks" to processedChunks.coerceAtLeast(0L),
                "qaCount" to qaCount.coerceAtLeast(0L),
                "trainingStatus" to safeStatus,
                "summary" to summary?.take(8000),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("users").document(uid).collection("training_books").document(safeId)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeKnowledgeVault(userId: String): Flow<List<KnowledgeEntry>> {
        val path = "users/$userId/knowledge_vault"
        return db.collection("users").document(userId).collection("knowledge_vault")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(KnowledgeEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                throw unwrapFirestoreCatch(error, OperationType.LIST, path, userId)
            }
    }

    suspend fun addKnowledgeEntry(userId: String, entry: KnowledgeEntry): Result<String> {
        val path = "users/$userId/knowledge_vault"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val docRef = db.collection("users").document(uid).collection("knowledge_vault").document()
            val safeSourceType = entry.sourceType.takeIf {
                it in listOf("book_training", "hunger_web", "context_anchor")
            } ?: "book_training"
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "bookId" to entry.bookId.ifBlank { "vault_item" }.take(128),
                "bookTitle" to entry.bookTitle.ifBlank { "Trained Knowledge" }.take(200),
                "question" to entry.question.ifBlank { "Core Concept" }.take(2000),
                "scenario" to entry.scenario.ifBlank { "Applied Scenario" }.take(4000),
                "expertAnswer" to entry.expertAnswer.ifBlank { "Expert insight" }.take(12000),
                "keywords" to entry.keywords?.take(15),
                "sourceType" to safeSourceType,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeHungerReports(userId: String): Flow<List<HungerReport>> {
        val path = "users/$userId/hunger_reports"
        return db.collection("users").document(userId).collection("hunger_reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(HungerReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                throw unwrapFirestoreCatch(error, OperationType.LIST, path, userId)
            }
    }

    suspend fun addHungerReport(userId: String, report: HungerReport): Result<String> {
        val path = "users/$userId/hunger_reports"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val docRef = db.collection("users").document(uid).collection("hunger_reports").document()
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "topic" to report.topic.ifBlank { "Self Improvement" }.take(200),
                    "filename" to report.filename.ifBlank { "hunger_report.txt" }.take(200),
                    "reportContent" to report.reportContent.ifBlank { "Knowledge gap summary" }.take(40000),
                    "learnedIntoVault" to report.learnedIntoVault,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
