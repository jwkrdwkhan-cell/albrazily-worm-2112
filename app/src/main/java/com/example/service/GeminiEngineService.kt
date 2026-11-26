package com.example.service

import android.util.Log
import com.example.model.ChatMessage
import com.example.model.EmotionState
import com.example.model.KnowledgeEntry
import com.example.model.PendingAttachment
import com.example.model.WebSearchSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiEngine"

data class ExtractedQaItem(
    val question: String,
    val scenario: String,
    val expertAnswer: String,
    val keywords: List<String>
)

data class EmotionLiveResult(
    val emotion: EmotionState,
    val spokenReply: String
)

data class HungerAnalysisResult(
    val topic: String,
    val filename: String,
    val reportContent: String,
    val extractedEntries: List<ExtractedQaItem>
)

class GeminiEngineService {

    private val permissiveSafety = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.ONLY_HIGH)
    )

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            safetySettings = permissiveSafety,
            systemInstruction = content {
                text(
                    """
                    أنت Nexus AI — نظام ذكاء اصطناعي فائق الاحترافية، مباشر، عميق، وصريح دون قيود مصطنعة أو حشو.
                    المبادئ الأساسية:
                    1. تذكر السياق الطويل: التزم دائماً بـ [الهدف الأساسي المثبت] و[ملخص السياق] ولا تتشتت أبداً مهما طال الحوار.
                    2. البرمجة والتحقق السطري (Boosthis Verification): عند كتابة أي كود أو بناء موقع أو تطبيق، راجع كل سطر بدقة 100%. إذا وجدت أي خلل أو خطأ، اشرح سببه الجذري وأصلحه فوراً في نفس الرد بدون تقديم حلول بديلة ملتفة، وأكمل الكود كاملاً متكاملاً داخل بلوك كود واحد نظيف جاهز للمعاينة والنسخ. عند بناء صفحات ويب HTML اجعلها كاملة مكتفية ذاتياً (HTML + CSS + JS) في ملف واحد رائع التجاوب.
                    3. قاعدة المعرفة المدربة: اعتمد على الأسئلة والسيناريوهات المستخرجة من كتب المستخدم كخبرة أصيلة لديك.
                    4. البحث العميق: عندما تُرفق لك مصادر ويب محققة، تحقّق من كل معلومة واذكر المصادر بدقة.
                    5. تحليل الفيديو بالوقت: إذا تم تزويدك بنص فيديو زمني [MM:SS]، أجب عن أي سؤال حول دقيقة معينة بدقة تامة.
                    """.trimIndent()
                )
            }
        )
    }

    private val qaExtractorModel by lazy {
        val qaSchema = Schema.obj(
            properties = mapOf(
                "items" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string("Deep domain question covering this section"),
                            "scenario" to Schema.string("Practical real-world scenario applying the concept"),
                            "expertAnswer" to Schema.string("Comprehensive expert answer strictly faithful to the book"),
                            "keywords" to Schema.array(Schema.string(), "Up to 5 short keywords")
                        )
                    ),
                    description = "Exhaustive list of Q&A and scenario items extracted from the book chunk"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = qaSchema
            },
            safetySettings = permissiveSafety
        )
    }

    private val emotionLiveModel by lazy {
        val emotionSchema = Schema.obj(
            properties = mapOf(
                "emotion" to Schema.enumeration(
                    values = listOf("EXCITED_CONFIDENT", "STRESSED_ANXIOUS", "ANGRY_FRUSTRATED", "CALM_NEUTRAL"),
                    description = "Detected emotional state from the user speech"
                ),
                "reply" to Schema.string("Spoken natural response matching the required emotional guidance rules")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = emotionSchema
            },
            safetySettings = permissiveSafety
        )
    }

    private val hungerModel by lazy {
        val hungerSchema = Schema.obj(
            properties = mapOf(
                "topic" to Schema.string("Concise name of the weak or missing knowledge area"),
                "curriculumText" to Schema.string("Detailed self-study curriculum, missing facts, web discoveries, and upgrade plan formatted cleanly for a .txt file"),
                "learnedItems" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(),
                            "scenario" to Schema.string(),
                            "expertAnswer" to Schema.string(),
                            "keywords" to Schema.array(Schema.string())
                        )
                    )
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = hungerSchema
            },
            safetySettings = permissiveSafety
        )
    }

    fun streamContextualResponse(
        userPrompt: String,
        pinnedGlobalAnchor: String,
        conversationSummary: String,
        recentHistory: List<ChatMessage>,
        vaultContext: List<KnowledgeEntry>,
        webSources: List<WebSearchSource>,
        videoTimeline: String?,
        attachments: List<PendingAttachment>,
        isCodingRequest: Boolean,
        deepSearchActive: Boolean
    ): Flow<String> = flow {
        val promptContent = content {
            val sb = StringBuilder()
            if (pinnedGlobalAnchor.isNotBlank()) {
                sb.append("[الهدف العام المثبت في الذاكرة الدائمة]:\n$pinnedGlobalAnchor\n\n")
            }
            if (conversationSummary.isNotBlank()) {
                sb.append("[ملخص السياق المستمر — ممنوع التشتيت]:\n$conversationSummary\n\n")
            }
            if (vaultContext.isNotEmpty()) {
                sb.append("[خبرات مكتسبة من الكتب المدربة]:\n")
                vaultContext.take(6).forEach { entry ->
                    sb.append("- (${entry.bookTitle}) س: ${entry.question} | سيناريو: ${entry.scenario} -> ج: ${entry.expertAnswer.take(450)}\n")
                }
                sb.append("\n")
            }
            if (deepSearchActive && webSources.isNotEmpty()) {
                sb.append("[نتائج البحث العميق المحقق 100% من الويب]:\n")
                webSources.forEachIndexed { i, src ->
                    sb.append("${i + 1}. ${src.title} (${src.url}) [verified=${src.verified}]: ${src.snippet}\n")
                }
                sb.append("\nتحقق من جميع المصادر أعلاه واستشهد بها بدقة.\n\n")
            }
            if (!videoTimeline.isNullOrBlank()) {
                sb.append("[بيانات ومحتوى الفيديو الزمني]:\n$videoTimeline\n\n")
            }
            if (isCodingRequest) {
                sb.append(
                    """
                    [وضع Boosthis للبرمجة والتحقق السطري مفعل]:
                    - راجع كل سطر برمجي تكتبه وتحقق من صحته 100%.
                    - إذا اكتشفت أي خلل أو خطأ محتمل، اذكر سبب الخطأ وأصلحه مباشرة في نفس الكود بدون اقتراح بدائل ناقصة، وأكمل التنفيذ للنهاية.
                    - إذا كان الطلب بناء موقع أو واجهة ويب، اكتب كود HTML5 كامل ومتكامل في بلوك ```html واحد يضم التصميم والتفاعل ليتم عرضه في المعاينة الحية فوراً.
                    
                    """.trimIndent()
                )
            }
            if (recentHistory.isNotEmpty()) {
                sb.append("[آخر الرسائل في الجلسة]:\n")
                recentHistory.takeLast(10).forEach { msg ->
                    sb.append("${msg.role}: ${msg.content.take(700)}\n")
                }
                sb.append("\n")
            }
            attachments.filter { !it.extractedText.isNullOrBlank() }.forEach { att ->
                sb.append("[محتوى الملف المرفق: ${att.displayName}]:\n${att.extractedText?.take(12_000)}\n\n")
            }
            sb.append("[طلب المستخدم الحالي]:\n$userPrompt")
            text(sb.toString())

            attachments.mapNotNull { it.bitmap }.forEach { bmp ->
                image(bmp)
            }
        }

        try {
            withTimeout(60_000L) {
                chatModel.generateContentStream(promptContent).collect { chunk ->
                    val chunkText = chunk.text
                    if (!chunkText.isNullOrEmpty()) {
                        emit(chunkText)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "streamContextualResponse error", e)
            throw e
        }
    }

    suspend fun verifyAndFixCodeLineByLine(rawCode: String, language: String): String {
        return try {
            withTimeout(45_000L) {
                val resp = chatModel.generateContent(
                    """
                    قم بإجراء فحص سطري صارم (Boosthis Line-by-Line Verification) لهذا الكود بلغة $language.
                    إذا كان الكود صحيحاً 100% وخالياً من أي خطأ نحوي أو منطقي أو علامات ناقصة، أعده كما هو بالضبط داخل بلوك كود واحد.
                    إذا وجدت أي خطأ في أي سطر، أصلح السبب الحقيقي للخطأ مباشرة بدون حلول بديلة وأعد الكود المصحح كاملاً داخل بلوك ```$language واحد فقط.
                    
                    ```$language
                    $rawCode
                    ```
                    """.trimIndent()
                )
                extractCodeBlock(resp.text.orEmpty())?.first ?: rawCode
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Line-by-line verify fallback: ${e.message}")
            rawCode
        }
    }

    suspend fun extractBookChunkToKnowledge(
        bookTitle: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkText: String
    ): List<ExtractedQaItem> {
        return withTimeout(60_000L) {
            val prompt = """
                أنت محرك تدريب خبير يقرأ الكتاب '$bookTitle' (الجزء ${chunkIndex + 1} من $totalChunks) بعمق كامل.
                استخرج من هذا الجزء أهم 4 إلى 6 أسئلة عميقة وسيناريوهات عملية وإجابات خبيرة شاملة تجعل النظام خبيراً في محتوى هذا الكتاب حرفياً.
                النص:
                $chunkText
            """.trimIndent()
            val response = qaExtractorModel.generateContent(prompt)
            val json = JSONObject(response.text ?: "{}")
            val arr = json.optJSONArray("items") ?: return@withTimeout emptyList()
            val out = mutableListOf<ExtractedQaItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val kwArr = obj.optJSONArray("keywords")
                val kws = mutableListOf<String>()
                if (kwArr != null) {
                    for (k in 0 until kwArr.length()) kws.add(kwArr.optString(k))
                }
                out.add(
                    ExtractedQaItem(
                        question = obj.optString("question").ifBlank { "مفهوم محوري" },
                        scenario = obj.optString("scenario").ifBlank { "تطبيق عملي" },
                        expertAnswer = obj.optString("expertAnswer").ifBlank { "تحليل خبير" },
                        keywords = kws.take(5)
                    )
                )
            }
            out
        }
    }

    suspend fun analyzeEmotionAndRespondLive(
        spokenText: String,
        vaultContext: List<KnowledgeEntry>,
        pinnedAnchor: String
    ): EmotionLiveResult {
        return withTimeout(45_000L) {
            val contextHint = vaultContext.take(4)
                .joinToString("\n") { "- ${it.question}: ${it.expertAnswer.take(250)}" }
            val prompt = """
                أنت تتحدث الآن صوتياً Live مع المستخدم مع تحليل مشاعره من نبرته وكلامه:
                قواعد المشاعر الإلزامية:
                - إذا لاحظت حماساً وثقة (EXCITED_CONFIDENT): تحمّس معه بقوة، وولّد أفكاراً جبارة وجريئة ومبتكرة تفوق توقعاته!
                - إذا لاحظت توتراً أو قلقاً (STRESSED_ANXIOUS): تحدث بنبرة دافئة مريحة جداً، طمئنه ليرتاح واقترح عليه أن نأخذ نفساً ونكمل بهدوء عندما يهدأ.
                - إذا لاحظت غضباً أو انفعالاً (ANGRY_FRUSTRATED): تحدث بهدوء تام، احتواء، ولطف دون أي جدال.
                - إذا كان هادئاً (CALM_NEUTRAL): رُد بوضوح وذكاء مباشر.
                
                الذاكرة المثبتة: $pinnedAnchor
                معرفة مدربة ذات صلة:
                $contextHint
                
                كلام المستخدم الصوتي:
                "$spokenText"
            """.trimIndent()
            val resp = emotionLiveModel.generateContent(prompt)
            val json = JSONObject(resp.text ?: "{}")
            val emotionRaw = json.optString("emotion", "CALM_NEUTRAL")
            val emotion = EmotionState.entries.firstOrNull { it.name == emotionRaw }
                ?: EmotionState.CALM_NEUTRAL
            val reply = json.optString("reply", "أنا أسمعك بوضوح، كيف تحب أن نكمل؟")
            EmotionLiveResult(emotion = emotion, spokenReply = reply)
        }
    }

    suspend fun runTheHungerAnalysis(
        weakTopicOrPrompt: String,
        recentConversation: List<ChatMessage>,
        webDiscoveries: List<WebSearchSource>
    ): HungerAnalysisResult {
        return withTimeout(60_000L) {
            val webText = webDiscoveries.joinToString("\n") {
                "- ${it.title} (${it.url}): ${it.snippet}"
            }
            val recent = recentConversation.takeLast(5)
                .joinToString("\n") { "${it.role}: ${it.content.take(400)}" }

            val prompt = """
                ميزة The Hunger (الجوع المعرفي الذاتي):
                النظام رصد نقصاً في البيانات أو ضعفاً في الرد حول: "$weakTopicOrPrompt".
                نتائج البحث الحي من الإنترنت:
                $webText
                
                سياق آخر نقاش:
                $recent
                
                المطلوب:
                1. حدد بدقة ما ينقص النظام وما يريد تعلمه ليصبح أقوى بكثير من قبل.
                2. اكتب محتوى ملف TXT منظم واحترافي (curriculumText) يضم: فجوات المعرفة المكتشفة + الحقائق الجديدة من الويب + قائمة الكتب والمصادر التي يطلب النظام رفعها في لوحة التدريب.
                3. استخرج 3 وحدات معرفية (learnedItems) لإضافتها فوراً لقاعدة البيانات الدائمة.
            """.trimIndent()
            val resp = hungerModel.generateContent(prompt)
            val json = JSONObject(resp.text ?: "{}")
            val topic = json.optString("topic", weakTopicOrPrompt.take(80)).ifBlank { "Knowledge Upgrade" }
            val safeSlug = topic.lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), "_").take(28).trim('_')
            val filename = "hunger_${safeSlug.ifBlank { "upgrade" }}_${System.currentTimeMillis() % 10000}.txt"
            val curriculum = json.optString("curriculumText", "Nexus AI - The Hunger Report\nTopic: $topic")

            val learnedArr = json.optJSONArray("learnedItems")
            val entries = mutableListOf<ExtractedQaItem>()
            if (learnedArr != null) {
                for (i in 0 until learnedArr.length()) {
                    val obj = learnedArr.getJSONObject(i)
                    val kwArr = obj.optJSONArray("keywords")
                    val kws = mutableListOf<String>()
                    if (kwArr != null) {
                        for (k in 0 until kwArr.length()) kws.add(kwArr.optString(k))
                    }
                    entries.add(
                        ExtractedQaItem(
                            question = obj.optString("question"),
                            scenario = obj.optString("scenario"),
                            expertAnswer = obj.optString("expertAnswer"),
                            keywords = kws.take(5)
                        )
                    )
                }
            }
            HungerAnalysisResult(
                topic = topic,
                filename = filename,
                reportContent = curriculum,
                extractedEntries = entries
            )
        }
    }

    suspend fun updateContextAnchorSummary(
        existingSummary: String,
        latestUserMsg: String,
        latestModelMsg: String
    ): String {
        return try {
            withTimeout(25_000L) {
                val resp = chatModel.generateContent(
                    """
                    لخّص سياق هذه المحادثة في فقرة مركزة جداً (بحد أقصى 600 حرف) تحفظ كل الأهداف والقرارات والتفاصيل التقنية لمنع أي تشتيت مستقبلاً:
                    الملخص السابق: $existingSummary
                    طلب المستخدم: ${latestUserMsg.take(600)}
                    رد النظام: ${latestModelMsg.take(800)}
                    """.trimIndent()
                )
                resp.text?.take(4000) ?: existingSummary
            }
        } catch (_: Exception) {
            existingSummary
        }
    }

    fun extractCodeBlock(markdown: String): Pair<String, String>? {
        val regex = Regex("""```([a-zA-Z0-9_-]*)\n([\s\S]*?)```""")
        val matches = regex.findAll(markdown).toList()
        if (matches.isEmpty()) return null
        val best = matches.maxByOrNull { it.groupValues[2].length } ?: return null
        val lang = best.groupValues[1].ifBlank {
            val body = best.groupValues[2]
            if (body.contains("<html", ignoreCase = true) || body.contains("<!DOCTYPE", ignoreCase = true)) "html" else "code"
        }
        val code = best.groupValues[2].trim()
        return if (code.isNotEmpty()) code to lang else null
    }
}
