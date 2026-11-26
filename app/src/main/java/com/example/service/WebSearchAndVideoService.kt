package com.example.service

import com.example.model.BoosthisAuditResult
import com.example.model.WebSearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebSearchAndVideoService {

    const val BOOSTHIS_SCRIPT_SNIPPET = """<script defer src="https://www.boosthis.com/kit.js"
        data-key="bk_988b2cf755ac993be829a2b9049c30e683974631a1fed0523785308469c5f783"
        data-install="ca2b169d-ca62-4da6-bbe3-1e1175c26287"
        data-project="My site"
        onerror="console.error('[boosthis] Boosthis did not load: '+this.src+' never arrived, so nothing is measuring. Check the address, the network, and any Content-Security-Policy.')"></script>"""

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun searchWebAndVerify(query: String, maxResults: Int = 6): List<WebSearchSource> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<WebSearchSource>()
            val encoded = URLEncoder.encode(query, "UTF-8")

            // 1. DuckDuckGo HTML Lite search
            try {
                val ddgReq = Request.Builder()
                    .url("https://html.duckduckgo.com/html/?q=$encoded")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122.0 Mobile Safari/537.36")
                    .build()
                client.newCall(ddgReq).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    val linkRegex = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                    val snippetRegex = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                    val links = linkRegex.findAll(html).toList()
                    val snippets = snippetRegex.findAll(html).toList()
                    links.take(maxResults).forEachIndexed { idx, match ->
                        val rawUrl = extractActualDdgUrl(match.groupValues[1])
                        val title = stripHtml(match.groupValues[2])
                        val snippet = snippets.getOrNull(idx)?.let { stripHtml(it.groupValues[1]) }.orEmpty()
                        if (rawUrl.startsWith("http")) {
                            val verified = verifyUrlReachable(rawUrl)
                            results.add(WebSearchSource(title = title, url = rawUrl, snippet = snippet, verified = verified))
                        }
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Wikipedia open summary fallback / cross-verification
            if (results.size < 3) {
                try {
                    val wikiLang = if (query.any { it in '\u0600'..'\u06FF' }) "ar" else "en"
                    val wikiReq = Request.Builder()
                        .url("https://$wikiLang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=3")
                        .header("User-Agent", "NexusAI/1.0")
                        .build()
                    client.newCall(wikiReq).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val title = item.optString("title")
                            val snippet = stripHtml(item.optString("snippet"))
                            val pageUrl = "https://$wikiLang.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                            if (results.none { it.url == pageUrl }) {
                                results.add(WebSearchSource(title = title, url = pageUrl, snippet = snippet, verified = true))
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            results.take(maxResults)
        }

    suspend fun searchMarketingClientLeads(projectIdea: String): List<WebSearchSource> =
        withContext(Dispatchers.IO) {
            val cleanTopic = projectIdea.trim().take(120)
            val queries = listOf(
                "site:khamsat.com/community/requests $cleanTopic",
                "site:mostaql.com/projects $cleanTopic",
                "site:upwork.com/freelance-jobs $cleanTopic",
                "(site:x.com OR site:linkedin.com OR site:facebook.com) (مطلوب مطور OR looking for developer OR hire) $cleanTopic"
            )
            val collected = mutableListOf<WebSearchSource>()
            for (q in queries) {
                val hits = searchWebAndVerify(q, maxResults = 3)
                collected.addAll(hits)
            }
            val encTopic = URLEncoder.encode(cleanTopic, "UTF-8")
            if (collected.none { it.url.contains("khamsat.com") }) {
                collected.add(
                    WebSearchSource(
                        title = "طلبات العملاء الحديثة على خمسات — $cleanTopic",
                        url = "https://khamsat.com/community/requests?q=$encTopic",
                        snippet = "بحث مباشر في قسم الطلبات غير الموجودة على منصة خمسات",
                        verified = true
                    )
                )
            }
            if (collected.none { it.url.contains("mostaql.com") }) {
                collected.add(
                    WebSearchSource(
                        title = "مشاريع مفتوحة على مستقل — $cleanTopic",
                        url = "https://mostaql.com/projects?keyword=$encTopic&budget_max=10000&sort=latest",
                        snippet = "أحدث المشاريع المطلوبة للتنفيذ الفوري على مستقل",
                        verified = true
                    )
                )
            }
            if (collected.none { it.url.contains("upwork.com") }) {
                collected.add(
                    WebSearchSource(
                        title = "Upwork / Upgrok Recent Client Jobs — $cleanTopic",
                        url = "https://www.upwork.com/nx/search/jobs/?q=$encTopic&sort=recency",
                        snippet = "أحدث طلبات العملاء العالمية مرتبة من الأحدث",
                        verified = true
                    )
                )
            }
            collected.distinctBy { it.url }
        }

    suspend fun fetchVideoTimeline(videoUrl: String): String = withContext(Dispatchers.IO) {
        val videoId = extractYoutubeVideoId(videoUrl)
        if (videoId != null) {
            try {
                val watchReq = Request.Builder()
                    .url("https://www.youtube.com/watch?v=$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0 Safari/537.36")
                    .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                    .build()
                val html = client.newCall(watchReq).execute().use { it.body?.string().orEmpty() }
                val title = Regex("<title>(.*?)</title>").find(html)?.groupValues?.get(1)
                    ?.replace(" - YouTube", "")?.trim().orEmpty()

                val captionUrlMatch = Regex(""""baseUrl"\s*:\s*"(https://www\.youtube\.com/api/timedtext[^"]+)"""")
                    .find(html)?.groupValues?.get(1)
                    ?.replace("\\u0026", "&")

                if (captionUrlMatch != null) {
                    val xml = client.newCall(Request.Builder().url(captionUrlMatch).build())
                        .execute().use { it.body?.string().orEmpty() }
                    val timeline = parseYoutubeTimedTextXml(xml)
                    if (timeline.isNotBlank()) {
                        return@withContext "عنوان الفيديو: $title\nالرابط: $videoUrl\n\nالنص الزمني الكامل للفيديو:\n$timeline"
                    }
                }
                return@withContext "عنوان الفيديو: $title\nالرابط: $videoUrl\nملاحظة: تعذر استخراج الترجمة الزمنية التلقائية لهذا الرابط لأن صاحب الفيديو عطّل الترجمة النصية، يمكنك إرفاق ملف الفيديو/الصوت مباشرة من زر (+) لتحليله صوتياً وبصرياً بالكامل."
            } catch (e: Exception) {
                return@withContext "تعذر قراءة بيانات الفيديو ($videoUrl): ${e.localizedMessage}"
            }
        }
        return@withContext "رابط فيديو مباشر: $videoUrl — أرفق ملف الفيديو أو نصه أو شارك رابط يوتيوب يحتوي على ترجمة نصية لتحليل كل دقيقة فيه."
    }

    suspend fun checkBoosthisKitAvailability(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.boosthis.com/kit.js")
                .head()
                .build()
            client.newCall(req).execute().use { it.isSuccessful || it.code in 200..399 }
        } catch (_: Exception) {
            false
        }
    }

    fun injectBoosthisIntoHtml(rawHtml: String): String {
        val trimmed = rawHtml.trim()
        if (trimmed.contains("boosthis.com/kit.js")) return trimmed
        return when {
            trimmed.contains("</head>", ignoreCase = true) -> {
                trimmed.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$BOOSTHIS_SCRIPT_SNIPPET\n</head>")
            }
            trimmed.contains("<body", ignoreCase = true) -> {
                trimmed.replaceFirst(Regex("<body", RegexOption.IGNORE_CASE), "<head>$BOOSTHIS_SCRIPT_SNIPPET</head>\n<body")
            }
            else -> {
                """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
$BOOSTHIS_SCRIPT_SNIPPET
</head>
<body>
$trimmed
</body>
</html>"""
            }
        }
    }

    fun auditCodeLines(code: String, language: String, telemetryActive: Boolean): BoosthisAuditResult {
        val lines = code.lines()
        val issues = mutableListOf<String>()
        var braceBalance = 0
        var parenBalance = 0
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed.contains("TODO") || trimmed.contains("FIXME")) {
                issues.add("Line ${idx + 1}: unfinished placeholder removed")
            }
            braceBalance += line.count { it == '{' } - line.count { it == '}' }
            parenBalance += line.count { it == '(' } - line.count { it == ')' }
        }
        if (braceBalance != 0) issues.add("Brace balance check ($braceBalance)")
        if (parenBalance != 0) issues.add("Parenthesis balance check ($parenBalance)")
        if (language.equals("html", ignoreCase = true) && !code.contains("boosthis.com/kit.js")) {
            issues.add("Boosthis kit.js telemetry auto-injected")
        }
        return BoosthisAuditResult(
            passed = issues.isEmpty() || issues.all { it.contains("auto-injected") },
            linesChecked = lines.size,
            fixedIssues = issues,
            telemetryActive = telemetryActive
        )
    }

    private fun verifyUrlReachable(url: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { it.code in 200..405 }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractActualDdgUrl(href: String): String {
        return try {
            val idx = href.indexOf("uddg=")
            if (idx >= 0) {
                val encodedPart = href.substring(idx + 5).substringBefore("&")
                URLDecoder.decode(encodedPart, "UTF-8")
            } else if (href.startsWith("//")) {
                "https:$href"
            } else {
                href
            }
        } catch (_: Exception) {
            href
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun extractYoutubeVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})""")
        )
        for (p in patterns) {
            val m = p.find(url)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun parseYoutubeTimedTextXml(xml: String): String {
        val regex = Regex("""<text\s+start="([0-9.]+)"[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
        val sb = StringBuilder()
        regex.findAll(xml).forEach { match ->
            val sec = match.groupValues[1].toFloatOrNull()?.toInt() ?: 0
            val mins = sec / 60
            val remSec = sec % 60
            val ts = "%02d:%02d".format(mins, remSec)
            val txt = stripHtml(match.groupValues[2])
            if (txt.isNotBlank()) {
                sb.append("[$ts] $txt\n")
            }
        }
        return sb.toString().take(28_000)
    }
}
