package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebase.NexusRepository
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.EmotionState
import com.example.model.HungerReport
import com.example.model.KnowledgeEntry
import com.example.model.PendingAttachment
import com.example.model.QueuedBookFile
import com.example.model.TrainingBook
import com.example.model.UiState
import com.example.model.UserWorkspace
import com.example.model.WebSearchSource
import com.example.service.GeminiEngineService
import com.example.service.WebSearchAndVideoService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "NexusVM"

enum class AppMode {
    CHAT_STUDIO,
    TRAINING_STUDIO
}

@OptIn(ExperimentalCoroutinesApi::class)
class NexusViewModel(
    private val repository: NexusRepository,
    private val gemini: GeminiEngineService,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?
) : ViewModel() {

    private val _appMode = MutableStateFlow(AppMode.CHAT_STUDIO)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _deepSearchEnabled = MutableStateFlow(false)
    val deepSearchEnabled: StateFlow<Boolean> = _deepSearchEnabled.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _activePhaseLabel = MutableStateFlow("")
    val activePhaseLabel: StateFlow<String> = _activePhaseLabel.asStateFlow()

    private val _uiBannerError = MutableStateFlow<String?>(null)
    val uiBannerError: StateFlow<String?> = _uiBannerError.asStateFlow()

    private val _detectedEmotion = MutableStateFlow(EmotionState.CALM_NEUTRAL)
    val detectedEmotion: StateFlow<EmotionState> = _detectedEmotion.asStateFlow()

    private val _lastLiveVoiceReply = MutableStateFlow<Pair<Long, String>?>(null)
    val lastLiveVoiceReply: StateFlow<Pair<Long, String>?> = _lastLiveVoiceReply.asStateFlow()

    private val _queuedBooks = MutableStateFlow<List<QueuedBookFile>>(emptyList())
    val queuedBooks: StateFlow<List<QueuedBookFile>> = _queuedBooks.asStateFlow()

    private val _trainingDaysInput = MutableStateFlow("7")
    val trainingDaysInput: StateFlow<String> = _trainingDaysInput.asStateFlow()

    private val _activeBookProgress = MutableStateFlow<String?>(null)
    val activeBookProgress: StateFlow<String?> = _activeBookProgress.asStateFlow()

    private val _cachedVideoTimelines = MutableStateFlow<Map<String, String>>(emptyMap())

    val workspaceState: StateFlow<UiState<UserWorkspace?>> =
        repository.observeWorkspace(currentUserId)
            .map<UserWorkspace?, UiState<UserWorkspace?>> { UiState.Success(it) }
            .catch { error -> emit(mapFirestoreError(error, "Failed to load workspace")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { list ->
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    _activeConversationId.value = list.first().id
                }
                UiState.Success(list)
            }
            .catch { error -> emit(mapFirestoreError(error, "Failed to load conversations")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeConversationId.flatMapLatest { convId ->
            if (convId == null) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error -> emit(mapFirestoreError(error, "Failed to load messages")) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val trainingBooksState: StateFlow<UiState<List<TrainingBook>>> =
        repository.observeTrainingBooks(currentUserId)
            .map<List<TrainingBook>, UiState<List<TrainingBook>>> { UiState.Success(it) }
            .catch { error -> emit(mapFirestoreError(error, "Failed to load training books")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val knowledgeVaultState: StateFlow<UiState<List<KnowledgeEntry>>> =
        repository.observeKnowledgeVault(currentUserId)
            .map<List<KnowledgeEntry>, UiState<List<KnowledgeEntry>>> { UiState.Success(it) }
            .catch { error -> emit(mapFirestoreError(error, "Failed to load Knowledge Vault")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val hungerReportsState: StateFlow<UiState<List<HungerReport>>> =
        repository.observeHungerReports(currentUserId)
            .map<List<HungerReport>, UiState<List<HungerReport>>> { UiState.Success(it) }
            .catch { error -> emit(mapFirestoreError(error, "Failed to load Hunger reports")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.ensureWorkspace(currentUserId, userDisplayName, userEmail)
                .onFailure { _uiBannerError.value = it.localizedMessage }
        }
    }

    private fun <T> mapFirestoreError(error: Throwable, fallback: String): UiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, fallback, root)
        return UiState.Error(root.localizedMessage ?: fallback)
    }

    fun switchMode(mode: AppMode) {
        _appMode.value = mode
    }

    fun toggleDeepSearch() {
        _deepSearchEnabled.value = !_deepSearchEnabled.value
    }

    fun addAttachments(items: List<PendingAttachment>) {
        if (items.isEmpty()) return
        _pendingAttachments.value = _pendingAttachments.value + items
    }

    fun removeAttachment(id: String) {
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id }
    }

    fun clearBannerError() {
        _uiBannerError.value = null
    }

    fun selectConversation(convId: String) {
        _activeConversationId.value = convId
    }

    fun startNewConversation() {
        viewModelScope.launch {
            repository.createConversation(currentUserId, "جلسة جديدة")
                .onSuccess { newId -> _activeConversationId.value = newId }
                .onFailure { _uiBannerError.value = it.localizedMessage }
        }
    }

    fun updatePinnedAnchor(anchor: String) {
        viewModelScope.launch {
            repository.updatePinnedContextAnchor(currentUserId, anchor)
                .onFailure { _uiBannerError.value = it.localizedMessage }
        }
    }

    fun sendUserMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        val attachmentsSnapshot = _pendingAttachments.value
        if ((prompt.isBlank() && attachmentsSnapshot.isEmpty()) || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""
            _uiBannerError.value = null
            _pendingAttachments.value = emptyList()

            try {
                val convId = ensureActiveConversation(prompt)
                val attachmentNames = attachmentsSnapshot.map { it.displayName }.takeIf { it.isNotEmpty() }

                repository.addMessage(
                    currentUserId,
                    convId,
                    ChatMessage(
                        userId = currentUserId,
                        role = "user",
                        content = prompt.ifBlank { "تحليل المرفقات المرسلة" },
                        deepSearchUsed = _deepSearchEnabled.value,
                        attachmentNames = attachmentNames
                    )
                )

                // Check for project completion marketing trigger: "كله اشتغل تمام"
                if (isProjectFinishedTrigger(prompt)) {
                    val lastProjectCtx = findRecentProjectContext()
                    repository.addMessage(
                        currentUserId,
                        convId,
                        ChatMessage(
                            userId = currentUserId,
                            role = "model",
                            content = "عظيم جداً! ما دام المشروع يعمل بنسبة 100% الآن، هل تريد أن أبحث لك في مواقع التسويق والسوشيال ميديا وخمسات ومستقل و Upwork/Upgrok عن عملاء حقيقيين يطلبون هذه الخدمة حالياً؟",
                            marketingPromptPending = true,
                            marketingProjectContext = lastProjectCtx
                        )
                    )
                    _isGenerating.value = false
                    _streamingText.value = null
                    return@launch
                }

                // Check for "The Hunger" explicit trigger
                if (isHungerTrigger(prompt)) {
                    executeTheHungerPipeline(convId, prompt)
                    return@launch
                }

                // 1. Video link timeline extraction if URL present
                var videoTimelineContext: String? = null
                val urlRegex = Regex("""https?://\S+""")
                val foundUrls = urlRegex.findAll(prompt).map { it.value }.toList()
                val videoUrl = foundUrls.firstOrNull {
                    it.contains("youtube.com") || it.contains("youtu.be") || it.endsWith(".mp4")
                }
                if (videoUrl != null) {
                    _activePhaseLabel.value = "جاري سمع وتحليل الفيديو بالكامل بالوقت..."
                    val timeline = WebSearchAndVideoService.fetchVideoTimeline(videoUrl)
                    _cachedVideoTimelines.value = _cachedVideoTimelines.value + (convId to timeline)
                    videoTimelineContext = timeline
                } else if (prompt.contains("الدقيق") || prompt.contains("فيديو") || prompt.contains("الفيديو")) {
                    videoTimelineContext = _cachedVideoTimelines.value[convId]
                }

                // 2. Deep Web Search if toggled ON
                val useDeepSearch = _deepSearchEnabled.value
                var webSources: List<WebSearchSource> = emptyList()
                if (useDeepSearch && prompt.isNotBlank()) {
                    _activePhaseLabel.value = "البحث العميق: فحص الويب والتحقق من المصادر 100%..."
                    webSources = WebSearchAndVideoService.searchWebAndVerify(prompt, maxResults = 6)
                }

                // 3. Knowledge Vault retrieval (from trained books & Hunger learnings)
                val allVault = (knowledgeVaultState.value as? UiState.Success)?.data.orEmpty()
                val relevantVault = retrieveRelevantVaultEntries(prompt, allVault)

                // 4. Boosthis coding detection
                val isCoding = detectCodingIntent(prompt)
                var boosthisOk = false
                if (isCoding) {
                    _activePhaseLabel.value = "تفعيل Boosthis API للتحقق السطري..."
                    boosthisOk = WebSearchAndVideoService.checkBoosthisKitAvailability()
                }

                // 5. Stream Gemini Response with Infinite Context Anchor
                _activePhaseLabel.value = if (useDeepSearch) {
                    "صياغة الرد الدقيق من المصادر المحققة..."
                } else if (isCoding) {
                    "كتابة الكود والتحقق منه عبر محرك Nexus..."
                } else {
                    "معالجة الذكاء العميق..."
                }

                val workspace = (workspaceState.value as? UiState.Success)?.data
                val pinnedAnchor = workspace?.pinnedContextAnchor.orEmpty()
                val conv = (conversationsState.value as? UiState.Success)?.data
                    ?.firstOrNull { it.id == convId }
                val history = (messagesState.value as? UiState.Success)?.data.orEmpty()

                val accumulated = StringBuilder()
                gemini.streamContextualResponse(
                    userPrompt = prompt,
                    pinnedGlobalAnchor = pinnedAnchor,
                    conversationSummary = conv?.contextSummary.orEmpty(),
                    recentHistory = history,
                    vaultContext = relevantVault,
                    webSources = webSources,
                    videoTimeline = videoTimelineContext,
                    attachments = attachmentsSnapshot,
                    isCodingRequest = isCoding,
                    deepSearchActive = useDeepSearch
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _streamingText.value = accumulated.toString()
                }

                var finalText = accumulated.toString().ifBlank {
                    "لم يتم استلام نص من المحرك، يرجى إعادة المحاولة."
                }
                var extractedCodePair = gemini.extractCodeBlock(finalText)

                // 6. Boosthis Line-by-Line Verification Loop
                if (isCoding && extractedCodePair != null) {
                    _activePhaseLabel.value = "Boosthis: مراجعة الكود سطراً بسطر وإصلاح أي خطأ..."
                    val verifiedCode = gemini.verifyAndFixCodeLineByLine(
                        extractedCodePair.first,
                        extractedCodePair.second
                    )
                    val finalCode = if (extractedCodePair.second.equals("html", ignoreCase = true)) {
                        WebSearchAndVideoService.injectBoosthisIntoHtml(verifiedCode)
                    } else {
                        verifiedCode
                    }
                    val audit = WebSearchAndVideoService.auditCodeLines(
                        finalCode,
                        extractedCodePair.second,
                        boosthisOk
                    )
                    extractedCodePair = finalCode to extractedCodePair.second
                    finalText += "\n\n✅ **تقرير التحقق السطري Boosthis**: تم فحص ${audit.linesChecked} سطراً" +
                        if (audit.fixedIssues.isNotEmpty()) " (${audit.fixedIssues.joinToString("، ")})" else " — الكود سليم 100%."
                }

                val sourceUrls = webSources.map { "${it.title} — ${it.url}" }.takeIf { it.isNotEmpty() }

                repository.addMessage(
                    currentUserId,
                    convId,
                    ChatMessage(
                        userId = currentUserId,
                        role = "model",
                        content = finalText,
                        codeBlock = extractedCodePair?.first,
                        codeLanguage = extractedCodePair?.second,
                        deepSearchUsed = useDeepSearch,
                        boosthisVerified = isCoding && extractedCodePair != null,
                        sources = sourceUrls
                    )
                )

                // Update rolling context anchor summary so memory never drifts
                val updatedSummary = gemini.updateContextAnchorSummary(
                    conv?.contextSummary.orEmpty(),
                    prompt,
                    finalText
                )
                val newTitle = if ((conv?.messageCount ?: 0L) <= 1L && prompt.isNotBlank()) {
                    prompt.take(45)
                } else null
                repository.updateConversationMeta(
                    currentUserId,
                    convId,
                    title = newTitle,
                    contextSummary = updatedSummary
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "sendUserMessage failed", e)
                _uiBannerError.value = e.localizedMessage ?: "حدث خطأ أثناء الاتصال بمحرك الذكاء الاصطناعي"
            } finally {
                _isGenerating.value = false
                _streamingText.value = null
                _activePhaseLabel.value = ""
            }
        }
    }

    fun respondToMarketingSearch(message: ChatMessage, accept: Boolean) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            repository.updateMessageMarketingPrompt(currentUserId, convId, message.id, pending = false)
            if (!accept) {
                repository.addMessage(
                    currentUserId,
                    convId,
                    ChatMessage(
                        userId = currentUserId,
                        role = "model",
                        content = "تم الإلغاء. أنا جاهز دائماً إذا أردت البحث عن عملاء لاحقاً."
                    )
                )
                return@launch
            }

            _isGenerating.value = true
            _activePhaseLabel.value = "البحث التسويقي الحي في خمسات ومستقل و Upwork والسوشيال ميديا..."
            try {
                val idea = message.marketingProjectContext?.ifBlank { "تطوير موقع وتطبيق" } ?: "تطوير موقع وتطبيق"
                val leads = WebSearchAndVideoService.searchMarketingClientLeads(idea)
                val leadText = buildString {
                    append("تم فحص منصات العمل الحر والسوشيال ميديا لطلبات قريبة لمشروعك (**$idea**):\n\n")
                    leads.forEachIndexed { idx, lead ->
                        append("${idx + 1}. **${lead.title}**\n${lead.snippet}\n${lead.url}\n\n")
                    }
                }
                repository.addMessage(
                    currentUserId,
                    convId,
                    ChatMessage(
                        userId = currentUserId,
                        role = "model",
                        content = leadText,
                        deepSearchUsed = true,
                        sources = leads.map { "${it.title} — ${it.url}" }
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiBannerError.value = e.localizedMessage ?: "فشل البحث التسويقي"
            } finally {
                _isGenerating.value = false
                _activePhaseLabel.value = ""
            }
        }
    }

    fun triggerTheHunger(topicHint: String) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""
            try {
                val convId = ensureActiveConversation("تحليل The Hunger")
                executeTheHungerPipeline(convId, topicHint)
            } finally {
                _isGenerating.value = false
                _streamingText.value = null
                _activePhaseLabel.value = ""
            }
        }
    }

    private suspend fun executeTheHungerPipeline(convId: String, topicHint: String) {
        _activePhaseLabel.value = "The Hunger: اكتشاف الفجوات والبحث في الإنترنت لتطوير الذات..."
        val recentMsgs = (messagesState.value as? UiState.Success)?.data.orEmpty()
        val effectiveTopic = topicHint.ifBlank {
            recentMsgs.lastOrNull { it.role == "user" }?.content?.take(120) ?: "تطوير القدرات الشاملة"
        }
        val webDiscoveries = WebSearchAndVideoService.searchWebAndVerify(effectiveTopic, maxResults = 6)
        val result = gemini.runTheHungerAnalysis(effectiveTopic, recentMsgs, webDiscoveries)

        // Store learned entries permanently in Firestore Knowledge Vault
        result.extractedEntries.forEach { qa ->
            repository.addKnowledgeEntry(
                currentUserId,
                KnowledgeEntry(
                    userId = currentUserId,
                    bookId = "hunger_${System.currentTimeMillis()}",
                    bookTitle = "The Hunger: ${result.topic}",
                    question = qa.question,
                    scenario = qa.scenario,
                    expertAnswer = qa.expertAnswer,
                    keywords = qa.keywords,
                    sourceType = "hunger_web"
                )
            )
        }

        repository.addHungerReport(
            currentUserId,
            HungerReport(
                userId = currentUserId,
                topic = result.topic,
                filename = result.filename,
                reportContent = result.reportContent,
                learnedIntoVault = true
            )
        )

        repository.addMessage(
            currentUserId,
            convId,
            ChatMessage(
                userId = currentUserId,
                role = "model",
                content = "🔥 **تم تفعيل ميزة The Hunger بنجاح**\n\nبحثت في الإنترنت عن المعلومات الناقصة حول **${result.topic}** وأضفت ${result.extractedEntries.size} وحدة معرفية جديدة لقاعدة البيانات الدائمة، وجهزت لك ملف TXT بالملفات والمهارات التي أريد تعلمها لأصبح أقوى:",
                deepSearchUsed = true,
                sources = webDiscoveries.map { "${it.title} — ${it.url}" },
                hungerTxtFilename = result.filename,
                hungerTxtContent = result.reportContent
            )
        )
    }

    fun processLiveVoiceInput(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isBlank() || _isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            _activePhaseLabel.value = "تحليل المشاعر والصوت الحي..."
            try {
                val convId = ensureActiveConversation("محادثة صوتية حية")
                val allVault = (knowledgeVaultState.value as? UiState.Success)?.data.orEmpty()
                val relevantVault = retrieveRelevantVaultEntries(clean, allVault)
                val workspace = (workspaceState.value as? UiState.Success)?.data
                val result = gemini.analyzeEmotionAndRespondLive(
                    spokenText = clean,
                    vaultContext = relevantVault,
                    pinnedAnchor = workspace?.pinnedContextAnchor.orEmpty()
                )
                _detectedEmotion.value = result.emotion
                _lastLiveVoiceReply.value = System.currentTimeMillis() to result.spokenReply

                repository.addMessage(
                    currentUserId,
                    convId,
                    ChatMessage(
                        userId = currentUserId,
                        role = "user",
                        content = "🎙️ $clean",
                        emotionTag = result.emotion.labelAr
                    )
                )
                repository.addMessage(
                    currentUserId,
                    convId,
                    ChatMessage(
                        userId = currentUserId,
                        role = "model",
                        content = result.spokenReply,
                        emotionTag = result.emotion.labelAr
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiBannerError.value = e.localizedMessage ?: "تعذر تحليل الصوت"
            } finally {
                _isGenerating.value = false
                _activePhaseLabel.value = ""
            }
        }
    }

    // --- Training Studio Pipeline ---

    fun updateTrainingDaysInput(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(3)
        _trainingDaysInput.value = digitsOnly
    }

    fun enqueueBooksForTraining(books: List<QueuedBookFile>) {
        if (books.isEmpty()) return
        val existingIds = _queuedBooks.value.map { it.title }.toSet()
        val uniqueNew = books.filterNot { it.title in existingIds }
        _queuedBooks.value = _queuedBooks.value + uniqueNew
    }

    fun lockAndStartBookTraining() {
        val booksToTrain = _queuedBooks.value
        val days = (_trainingDaysInput.value.toLongOrNull() ?: 7L).coerceIn(1L, 365L)
        if (booksToTrain.isEmpty()) {
            _uiBannerError.value = "يرجى رفع كتاب أو ملف واحد على الأقل أولاً."
            return
        }

        viewModelScope.launch {
            val unlockAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
            repository.updateTrainingLock(
                userId = currentUserId,
                locked = true,
                days = days,
                unlockAtMillis = unlockAt,
                status = "locked"
            ).onFailure {
                _uiBannerError.value = it.localizedMessage
                return@launch
            }

            try {
                booksToTrain.forEachIndexed { bIdx, book ->
                    _activeBookProgress.value = "جاري قراءة الكتاب (${bIdx + 1}/${booksToTrain.size}): ${book.title}"
                    repository.createTrainingBook(
                        userId = currentUserId,
                        bookId = book.id,
                        title = book.title,
                        mimeType = book.mimeType,
                        totalChunks = book.chunks.size.toLong()
                    )

                    var totalQaForBook = 0L
                    book.chunks.forEachIndexed { cIdx, chunk ->
                        _activeBookProgress.value =
                            "كتاب: ${book.title} • تحويل الفصل ${cIdx + 1}/${book.chunks.size} إلى أسئلة وسيناريوهات..."
                        val qaList = gemini.extractBookChunkToKnowledge(
                            bookTitle = book.title,
                            chunkIndex = cIdx,
                            totalChunks = book.chunks.size,
                            chunkText = chunk
                        )
                        qaList.forEach { item ->
                            repository.addKnowledgeEntry(
                                currentUserId,
                                KnowledgeEntry(
                                    userId = currentUserId,
                                    bookId = book.id,
                                    bookTitle = book.title,
                                    question = item.question,
                                    scenario = item.scenario,
                                    expertAnswer = item.expertAnswer,
                                    keywords = item.keywords,
                                    sourceType = "book_training"
                                )
                            )
                            totalQaForBook++
                        }
                        repository.updateTrainingBookProgress(
                            userId = currentUserId,
                            bookId = book.id,
                            processedChunks = (cIdx + 1).toLong(),
                            qaCount = totalQaForBook,
                            status = if (cIdx + 1 == book.chunks.size) "completed" else "reading",
                            summary = if (cIdx + 1 == book.chunks.size) {
                                "تم هضم الكتاب بالكامل وتحويله إلى $totalQaForBook سؤال وسيناريو خبير في قاعدة البيانات الدائمة."
                            } else null
                        )
                    }
                }
                _queuedBooks.value = emptyList()
                _activeBookProgress.value = "اكتمل التدريب العميق لكل الكتب! المحرك الآن خبير كامل في كل محتواها."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Book training error", e)
                _uiBannerError.value = e.localizedMessage ?: "حدث خطأ أثناء التدريب"
            }
        }
    }

    fun unlockTrainingStudio() {
        viewModelScope.launch {
            repository.updateTrainingLock(
                userId = currentUserId,
                locked = false,
                days = (_trainingDaysInput.value.toLongOrNull() ?: 7L).coerceIn(1L, 365L),
                unlockAtMillis = 0L,
                status = "completed"
            )
            _activeBookProgress.value = null
        }
    }

    private suspend fun ensureActiveConversation(initialPrompt: String): String {
        val current = _activeConversationId.value
        if (current != null) return current
        val existing = (conversationsState.value as? UiState.Success)?.data.orEmpty()
        if (existing.isNotEmpty()) {
            _activeConversationId.value = existing.first().id
            return existing.first().id
        }
        val title = initialPrompt.trim().take(45).ifBlank { "جلسة ذكاء اصطناعي" }
        val created = repository.createConversation(currentUserId, title).getOrThrow()
        _activeConversationId.value = created
        return created
    }

    private fun retrieveRelevantVaultEntries(
        query: String,
        vault: List<KnowledgeEntry>
    ): List<KnowledgeEntry> {
        if (vault.isEmpty()) return emptyList()
        val tokens = query.lowercase().split(Regex("[\\s,.;:!?،]+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return vault.take(5)
        return vault.sortedByDescending { entry ->
            val hay = "${entry.bookTitle} ${entry.question} ${entry.scenario} ${entry.expertAnswer}".lowercase()
            tokens.count { hay.contains(it) }
        }.take(6)
    }

    private fun detectCodingIntent(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val keywords = listOf(
            "كود", "برمج", "موقع", "تطبيق", "صفحة", "html", "css", "javascript", "kotlin",
            "python", "react", "api", "script", "code", "website", "app", "ui", "sql", "android"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isProjectFinishedTrigger(prompt: String): Boolean {
        val norm = prompt.replace("أ", "ا").replace("إ", "ا").trim()
        return norm.contains("كله اشتغل تمام") || norm.contains("كله شغال تمام") || norm.contains("المشروع اشتغل تمام")
    }

    private fun isHungerTrigger(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("the hunger") || lower.contains("ردك ضعيف") || lower.contains("ناقصك داتا") || lower.contains("ناقصك بيانات")
    }

    private fun findRecentProjectContext(): String {
        val msgs = (messagesState.value as? UiState.Success)?.data.orEmpty()
        return msgs.lastOrNull { it.role == "user" && !isProjectFinishedTrigger(it.content) }
            ?.content?.take(120) ?: "تصميم وبناء موقع وتطبيق"
    }
}
