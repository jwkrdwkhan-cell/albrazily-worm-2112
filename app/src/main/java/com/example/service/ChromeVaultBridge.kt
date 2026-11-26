package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.HungerReport
import com.example.model.KnowledgeEntry
import com.example.model.TrainingBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

object ChromeVaultBridge {

    private const val TAG = "ChromeVaultBridge"
    private const val VAULT_PORT = 48921

    @Volatile
    private var serverRunning = false

    @Volatile
    private var latestVaultHtml: String = "<html><body><h1>Nexus AI Vault Ready</h1></body></html>"

    fun buildVaultHtml(
        books: List<TrainingBook>,
        knowledge: List<KnowledgeEntry>,
        hungerReports: List<HungerReport>,
        vaultToken: String
    ): String {
        val knowledgeJson = JSONArray().apply {
            knowledge.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("bookTitle", item.bookTitle)
                        put("question", item.question)
                        put("scenario", item.scenario)
                        put("expertAnswer", item.expertAnswer)
                        put("sourceType", item.sourceType)
                    }
                )
            }
        }.toString()

        val booksJson = JSONArray().apply {
            books.forEach { b ->
                put(
                    JSONObject().apply {
                        put("title", b.title)
                        put("status", b.trainingStatus)
                        put("qaCount", b.qaCount)
                    }
                )
            }
        }.toString()

        return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Nexus AI — Chrome Permanent Knowledge Vault</title>
${WebSearchAndVideoService.BOOSTHIS_SCRIPT_SNIPPET}
<style>
  body { background:#07090F; color:#F4F6FA; font-family:system-ui,-apple-system,sans-serif; margin:0; padding:20px; }
  .header { border:1px solid #F5C451; border-radius:16px; padding:18px; background:#0E1320; margin-bottom:18px; }
  h1 { color:#F5C451; margin:0 0 8px 0; font-size:22px; }
  .badge { display:inline-block; background:#141B2D; border:1px solid #2CE6FF; color:#2CE6FF; padding:4px 10px; border-radius:99px; font-size:12px; }
  .card { background:#141B2D; border:1px solid #27334E; border-radius:14px; padding:14px; margin-bottom:12px; }
  .q { color:#F5C451; font-weight:700; margin-bottom:6px; }
  .s { color:#2CE6FF; font-size:13px; margin-bottom:6px; }
  .a { color:#E2E8F5; line-height:1.6; font-size:14px; }
</style>
</head>
<body>
  <div class="header">
    <h1>قاعدة المعرفة الدائمة المرتبطة بمتصفح Chrome</h1>
    <div class="badge">Token: $vaultToken • الكتب: ${books.size} • الوحدات المعرفية: ${knowledge.size} • تقارير Hunger: ${hungerReports.size}</div>
    <p id="syncStatus" style="color:#2CE59B;font-size:13px;margin-top:10px;">جاري المزامنة الدائمة مع Chrome IndexedDB و LocalStorage...</p>
  </div>
  <div id="list"></div>
  <script>
    const vaultToken = ${JSONObject.quote(vaultToken)};
    const entries = $knowledgeJson;
    const books = $booksJson;
    try {
      localStorage.setItem('nexus_permanent_vault_' + vaultToken, JSON.stringify({ updatedAt: Date.now(), books, entries }));
      const req = indexedDB.open('NexusAIPermanentVaultDB', 1);
      req.onupgradeneeded = (e) => {
        const db = e.target.result;
        if (!db.objectStoreNames.contains('knowledge')) db.createObjectStore('knowledge', { keyPath: 'id' });
      };
      req.onsuccess = (e) => {
        const db = e.target.result;
        const tx = db.transaction('knowledge', 'readwrite');
        const store = tx.objectStore('knowledge');
        entries.forEach(item => store.put(item));
        document.getElementById('syncStatus').textContent = 'تم الحفظ الدائم في Chrome IndexedDB & LocalStorage بنجاح (' + entries.length + ' سجل معرفي غير قابل للحذف).';
      };
    } catch (err) {
      document.getElementById('syncStatus').textContent = 'Vault active in session: ' + err.message;
    }
    const listEl = document.getElementById('list');
    entries.forEach(item => {
      const div = document.createElement('div');
      div.className = 'card';
      div.innerHTML = '<div class="q">' + item.question + '</div>' +
                      '<div class="s">' + item.bookTitle + ' • ' + item.scenario + '</div>' +
                      '<div class="a">' + item.expertAnswer + '</div>';
      listEl.appendChild(div);
    });
  </script>
</body>
</html>"""
    }

    fun openInChromeVault(
        context: Context,
        books: List<TrainingBook>,
        knowledge: List<KnowledgeEntry>,
        hungerReports: List<HungerReport>,
        vaultToken: String
    ) {
        latestVaultHtml = buildVaultHtml(books, knowledge, hungerReports, vaultToken)
        startLocalServerIfNeeded()
        val url = "http://127.0.0.1:$VAULT_PORT/"
        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chromeIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    suspend fun saveAndShareTxtFile(
        context: Context,
        filename: String,
        content: String
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "hunger_exports").apply { mkdirs() }
        val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(dir, safeName)
        file.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareTxtFile(context: Context, uri: Uri, filename: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف Hunger TXT").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun startLocalServerIfNeeded() {
        if (serverRunning) return
        serverRunning = true
        Thread {
            try {
                val server = ServerSocket(VAULT_PORT)
                while (serverRunning) {
                    val socket: Socket = server.accept()
                    handleSocket(socket)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chrome vault server stopped: ${e.message}")
                serverRunning = false
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun handleSocket(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val body = latestVaultHtml.toByteArray(Charsets.UTF_8)
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)
                s.getOutputStream().apply {
                    write(headers)
                    write(body)
                    flush()
                }
            }
        } catch (_: Exception) {
        }
    }
}
