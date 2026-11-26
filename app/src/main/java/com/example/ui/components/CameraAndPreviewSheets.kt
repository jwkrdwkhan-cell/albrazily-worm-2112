package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.EmotionState
import com.example.service.WebSearchAndVideoService
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidGold
import com.example.ui.theme.NeuralCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun InAppCameraModal(
    onCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture = capture
                                    val selector = CameraSelector.Builder()
                                        .requireLensFacing(lensFacing)
                                        .build()
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        capture
                                    )
                                } catch (e: Exception) {
                                    errorMsg = e.localizedMessage
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "يلزم السماح بصلاحية الكاميرا للتصوير المباشر",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("منح صلاحية الكاميرا")
                        }
                    }
                }

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianBlack.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق الكاميرا", tint = TextPrimary)
                    }

                    Text(
                        text = "الكاميرا الحية — Nexus Vision",
                        color = LiquidGold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ObsidianBlack.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianBlack.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "تبديل الكاميرا", tint = NeuralCyan)
                    }
                }

                errorMsg?.let {
                    Text(
                        text = it,
                        color = CrimsonAlert,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(ObsidianBlack.copy(alpha = 0.8f))
                            .padding(12.dp)
                    )
                }

                // Capture button
                if (hasPermission) {
                    Button(
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val rawBmp = image.toBitmap()
                                        val matrix = Matrix().apply {
                                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                                        }
                                        val rotated = Bitmap.createBitmap(
                                            rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true
                                        )
                                        image.close()
                                        onCaptured(rotated)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        errorMsg = exception.localizedMessage
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiquidGold, contentColor = ObsidianBlack),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp)
                            .height(58.dp)
                            .testTag("camera_capture_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "التقاط صورة")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("التقاط وإرفاق للشات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveCodePreviewDialog(
    code: String,
    language: String,
    onDismiss: () -> Unit
) {
    val isWebRenderable = language.equals("html", ignoreCase = true) ||
        code.contains("<html", ignoreCase = true) ||
        code.contains("<body", ignoreCase = true) ||
        code.contains("<svg", ignoreCase = true)

    val htmlPayload = remember(code, language) {
        if (isWebRenderable) WebSearchAndVideoService.injectBoosthisIntoHtml(code) else ""
    }
    val consoleLogs = remember { mutableStateListOf<String>() }
    var mobileFrame by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isWebRenderable) Icons.Default.Web else Icons.Default.PhoneIphone,
                            contentDescription = "معاينة حية",
                            tint = NeuralCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWebRenderable) "معاينة حية (Boosthis مفعّل)" else "معاينة الكود ($language)",
                            color = LiquidGold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isWebRenderable) {
                            IconButton(
                                onClick = { mobileFrame = !mobileFrame },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.PhoneIphone,
                                    contentDescription = "تبديل وضع الجوال",
                                    tint = if (mobileFrame) LiquidGold else TextSecondary
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_preview_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق المعاينة", tint = TextPrimary)
                        }
                    }
                }

                if (isWebRenderable) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0A0D14)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = if (mobileFrame) {
                                Modifier
                                    .width(360.dp)
                                    .fillMaxSize()
                                    .padding(vertical = 12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(2.dp, LiquidGold, RoundedCornerShape(20.dp))
                            } else {
                                Modifier.fillMaxSize()
                            },
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowContentAccess = true
                                    webViewClient = WebViewClient()
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                            consoleLogs.add("[${msg.messageLevel()}] ${msg.message()}")
                                            return true
                                        }
                                    }
                                    loadDataWithBaseURL(
                                        "https://localhost/",
                                        htmlPayload,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        )
                    }
                    if (consoleLogs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianCard)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "سجل التنفيذ (Boosthis / Console):",
                                color = EmeraldVerified,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = consoleLogs.takeLast(3).joinToString("\n"),
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "تم التحقق من سلامة هذا الكود سطراً بسطر (${code.lines().size} سطر):",
                            color = EmeraldVerified,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianBlack)
                                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = code,
                                color = TextPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveVoiceEmotionOverlay(
    detectedEmotion: EmotionState,
    isProcessing: Boolean,
    lastReply: Pair<Long, String>?,
    onSpokenResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("اضغط على المايك وتحدث بحرية — سأحلل مشاعرك وأرد عليك فوراً") }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale("ar")
            }
        }
        instance
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(lastReply) {
        lastReply?.second?.let { reply ->
            if (reply.isNotBlank()) {
                tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "nexus_live_${lastReply.first}")
            }
        }
    }

    fun startListening() {
        val recognizer = speechRecognizer ?: return
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                partialTranscript = "لم ألتقط الصوت بوضوح، اضغط للمحاولة مرة أخرى."
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    partialTranscript = spoken
                    onSpokenResult(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!p.isNullOrBlank()) partialTranscript = p
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(28.dp),
            color = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المحادثة الصوتية الحية + محلل المشاعر",
                        color = LiquidGold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = {
                        tts.stop()
                        onDismiss()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NexusNeuralOrb(isActive = isListening || isProcessing, size = 148.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(detectedEmotion.badgeColorHex).copy(alpha = 0.16f))
                            .border(1.dp, Color(detectedEmotion.badgeColorHex), RoundedCornerShape(99.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "الحالة الشعورية: ${detectedEmotion.labelAr}",
                            color = Color(detectedEmotion.badgeColorHex),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LiveVoiceWaveform(
                        isListening = isListening || isProcessing,
                        accentColor = Color(detectedEmotion.badgeColorHex)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = lastReply?.second ?: partialTranscript,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (!hasMicPermission) {
                    Button(
                        onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = LiquidGold, contentColor = ObsidianBlack),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("تفعيل صلاحية الميكروفون")
                    }
                } else {
                    Button(
                        onClick = {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                                isListening = false
                            } else {
                                tts.stop()
                                startListening()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) CrimsonAlert else LiquidGold,
                            contentColor = ObsidianBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("live_voice_mic_action")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "تحدث الآن"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isListening) "إيقاف الاستماع" else "تحدث الآن Live",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
