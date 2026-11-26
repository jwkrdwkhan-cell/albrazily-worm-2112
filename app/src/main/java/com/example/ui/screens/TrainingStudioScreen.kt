package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiState
import com.example.service.AttachmentProcessor
import com.example.service.ChromeVaultBridge
import com.example.ui.components.AnimatedAiThinkingBar
import com.example.ui.components.NexusNeuralOrb
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.LiquidGold
import com.example.ui.theme.NeuralCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.NexusViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun TrainingStudioScreen(
    viewModel: NexusViewModel,
    onBackToChat: () -> Unit
) {
    BackHandler { onBackToChat() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val queuedBooks by viewModel.queuedBooks.collectAsStateWithLifecycle()
    val trainingDaysInput by viewModel.trainingDaysInput.collectAsStateWithLifecycle()
    val activeBookProgress by viewModel.activeBookProgress.collectAsStateWithLifecycle()
    val trainingBooksState by viewModel.trainingBooksState.collectAsStateWithLifecycle()
    val knowledgeVaultState by viewModel.knowledgeVaultState.collectAsStateWithLifecycle()
    val hungerReportsState by viewModel.hungerReportsState.collectAsStateWithLifecycle()

    val workspace = (workspaceState as? UiState.Success)?.data
    val books = (trainingBooksState as? UiState.Success)?.data.orEmpty()
    val vaultEntries = (knowledgeVaultState as? UiState.Success)?.data.orEmpty()
    val hungerReports = (hungerReportsState as? UiState.Success)?.data.orEmpty()
    val isLocked = workspace?.trainingLocked == true

    val bookUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val prepared = uris.map { AttachmentProcessor.prepareBookForTraining(context, it) }
                viewModel.enqueueBooksForTraining(prepared)
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBackToChat,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("back_to_chat_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة للشات", tint = LiquidGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("محرك الذكاء", color = LiquidGold, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "لوحة التدريب العميق",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = {
                        ChromeVaultBridge.openInChromeVault(
                            context = context,
                            books = books,
                            knowledge = vaultEntries,
                            hungerReports = hungerReports,
                            vaultToken = workspace?.chromeVaultToken ?: "default"
                        )
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = "Chrome Vault", tint = NeuralCyan)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Luxury Training Header Banner
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(ObsidianCard, ObsidianElevated))
                        )
                        .border(1.2.dp, LiquidGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NexusNeuralOrb(isActive = activeBookProgress != null || isLocked, size = 90.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "استوديو تحويل الكتب إلى خبرة مطلقة",
                        style = MaterialTheme.typography.titleLarge,
                        color = LiquidGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يقرأ النظام كل كتاب بالكامل فصلاً بفصل، ويحوّله بالكامل إلى أسئلة وسيناريوهات مخزنة للأبد في السحابة ومتصفح Chrome.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "الكتب المكتملة: ${books.count { it.trainingStatus == "completed" }} • الوحدات الخبيرة المحفوظة: ${vaultEntries.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeuralCyan
                    )
                }
            }

            // Lock Banner if Studio is currently locked
            if (isLocked) {
                item {
                    val remainingMs = ((workspace?.trainingUnlockAtMillis ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
                    val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMs)
                    val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingMs) % 24
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(ObsidianElevated)
                            .border(1.5.dp, CrimsonAlert, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CrimsonAlert)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "وضع القفل والتدريب نشط (${workspace?.trainingDays ?: 7} يوم)",
                                color = CrimsonAlert,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "الوقت المتبقي على القفل: $remainingDays يوم و $remainingHours ساعة",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { viewModel.unlockTrainingStudio() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = LiquidGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فك القفل يدوياً", color = LiquidGold)
                        }
                    }
                }
            }

            // THE 3 CORE LUXURY TRAINING BUTTONS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(ObsidianCard)
                        .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Upload Unlimited Files Button
                    Button(
                        onClick = { bookUploadLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeuralCyan,
                            contentColor = ObsidianBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("training_upload_files_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "رفع الملفات")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "1. رفع الملفات والكتب (عدد غير محدود)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Queued files preview
                    AnimatedVisibility(visible = queuedBooks.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianElevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "الملفات الجاهزة للتدريب (${queuedBooks.size}):",
                                color = LiquidGold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            queuedBooks.forEach { qb ->
                                Text(
                                    text = "• ${qb.title} (${qb.chunks.size} فصل)",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // 2. Number of Training Days Selector/Input
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianElevated)
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = LiquidGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "2. قائمة عدد أيام القفل والتدريب",
                                color = LiquidGold,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = trainingDaysInput,
                            onValueChange = viewModel::updateTrainingDaysInput,
                            label = { Text("اكتب عدد الأيام (1 - 365)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LiquidGold,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("training_days_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("3", "7", "14", "30").forEach { preset ->
                                Surface(
                                    onClick = { viewModel.updateTrainingDaysInput(preset) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (trainingDaysInput == preset) LiquidGold else ObsidianCard,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$preset أيام",
                                        color = if (trainingDaysInput == preset) ObsidianBlack else TextPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 3. Lock & Train Execution Button
                    Button(
                        onClick = { viewModel.lockAndStartBookTraining() },
                        enabled = queuedBooks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiquidGold,
                            contentColor = ObsidianBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("training_lock_start_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "القفل والتدريب")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "3. القفل والتدريب الآن",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Live Progress Indicator
            activeBookProgress?.let { status ->
                item {
                    AnimatedAiThinkingBar(phaseLabel = status)
                }
            }

            // Books Mastery List
            if (books.isNotEmpty()) {
                item {
                    Text(
                        text = "الكتب قيد التدريب والمكتملة",
                        style = MaterialTheme.typography.titleLarge,
                        color = LiquidGold
                    )
                }
                items(books, key = { it.id }) { book ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianCard)
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, tint = NeuralCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(book.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            }
                            if (book.trainingStatus == "completed") {
                                Icon(Icons.Default.CheckCircle, contentDescription = "مكتمل", tint = EmeraldVerified)
                            }
                        }
                        val progress = (book.processedChunks.toFloat() / book.totalChunks.coerceAtLeast(1L).toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = EmeraldVerified,
                            trackColor = ObsidianElevated,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "الفصول: ${book.processedChunks}/${book.totalChunks} • الأسئلة والسيناريوهات: ${book.qaCount}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Permanent Knowledge Vault Explorer
            if (vaultEntries.isNotEmpty()) {
                item {
                    Text(
                        text = "قاعدة الأسئلة والسيناريوهات الدائمة (${vaultEntries.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeuralCyan
                    )
                }
                items(vaultEntries.take(30), key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianCard)
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(entry.question, color = LiquidGold, style = MaterialTheme.typography.titleMedium)
                        Text("السيناريو: ${entry.scenario}", color = NeuralCyan, style = MaterialTheme.typography.bodyMedium)
                        Text(entry.expertAnswer, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
