package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AttachmentKind
import com.example.model.ChatMessage
import com.example.model.PendingAttachment
import com.example.model.UiState
import com.example.service.AttachmentProcessor
import com.example.service.ChromeVaultBridge
import com.example.ui.components.AnimatedAiThinkingBar
import com.example.ui.components.AnimatedMessageReveal
import com.example.ui.components.GlowingStreamingContainer
import com.example.ui.components.InAppCameraModal
import com.example.ui.components.LiveCodePreviewDialog
import com.example.ui.components.LiveVoiceEmotionOverlay
import com.example.ui.components.NexusNeuralOrb
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPulse
import com.example.viewmodel.NexusViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatStudioScreen(
    viewModel: NexusViewModel,
    onOpenTrainingStudio: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val trainingBooksState by viewModel.trainingBooksState.collectAsStateWithLifecycle()
    val knowledgeState by viewModel.knowledgeVaultState.collectAsStateWithLifecycle()
    val hungerReportsState by viewModel.hungerReportsState.collectAsStateWithLifecycle()

    val deepSearchEnabled by viewModel.deepSearchEnabled.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val activePhaseLabel by viewModel.activePhaseLabel.collectAsStateWithLifecycle()
    val bannerError by viewModel.uiBannerError.collectAsStateWithLifecycle()
    val detectedEmotion by viewModel.detectedEmotion.collectAsStateWithLifecycle()
    val lastLiveReply by viewModel.lastLiveVoiceReply.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var plusMenuExpanded by remember { mutableStateOf(false) }
    var showCameraModal by remember { mutableStateOf(false) }
    var showLiveVoice by remember { mutableStateOf(false) }
    var showContextAnchorDialog by remember { mutableStateOf(false) }
    var previewCodePair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var txtViewerPair by remember { mutableStateOf<Pair<String, String>?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val processed = uris.map { AttachmentProcessor.processImageUri(context, it, AttachmentKind.IMAGE) }
                viewModel.addAttachments(processed)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val processed = uris.map { AttachmentProcessor.processDocumentUri(context, it) }
                viewModel.addAttachments(processed)
            }
        }
    }

    LaunchedEffect(bannerError) {
        bannerError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerError()
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    val workspace = (workspaceState as? UiState.Success)?.data
    val books = (trainingBooksState as? UiState.Success)?.data.orEmpty()
    val vaultEntries = (knowledgeState as? UiState.Success)?.data.orEmpty()
    val hungerReports = (hungerReportsState as? UiState.Success)?.data.orEmpty()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ObsidianSurface,
                drawerContentColor = TextPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "سجل الجلسات والذاكرة الدائمة",
                        style = MaterialTheme.typography.titleLarge,
                        color = LiquidGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.startNewConversation()
                            scope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiquidGold, contentColor = ObsidianBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("محادثة جديدة")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val convs = (conversationsState as? UiState.Success)?.data.orEmpty()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(convs, key = { it.id }) { conv ->
                            val selected = conv.id == activeConvId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) ObsidianElevated else ObsidianCard)
                                    .border(
                                        1.dp,
                                        if (selected) LiquidGold else BorderGlass,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        viewModel.selectConversation(conv.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (selected) LiquidGold else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = conv.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CrimsonAlert)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تسجيل الخروج", color = CrimsonAlert)
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = ObsidianBlack,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface.copy(alpha = 0.96f))
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Explicit physical Left-to-Right top row so "Training Studio" sits on Top-Left as requested
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // TOP-LEFT: Training Studio Button
                            Surface(
                                onClick = onOpenTrainingStudio,
                                shape = RoundedCornerShape(16.dp),
                                color = ObsidianElevated,
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, LiquidGold),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("training_studio_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = "لوحة التدريب",
                                        tint = LiquidGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.btn_training_studio),
                                        color = LiquidGold,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Center Luxury Logo & Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_nexus_logo),
                                    contentDescription = "Nexus Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, LiquidGold, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NEXUS AI",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Top-Right History Drawer Button
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = "سجل المحادثات", tint = TextPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Utility Action Strip: The Hunger, Context Anchor, Chrome Permanent Vault
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UtilityChip(
                            icon = Icons.Default.LocalFireDepartment,
                            label = "The Hunger",
                            tint = Color(0xFFFF8A47),
                            testTag = "the_hunger_button"
                        ) {
                            viewModel.triggerTheHunger(inputText)
                        }

                        UtilityChip(
                            icon = Icons.Default.Bookmark,
                            label = "تثبيت السياق (${if (workspace?.pinnedContextAnchor.isNullOrBlank()) "فارغ" else "نشط"})",
                            tint = VioletPulse,
                            testTag = "context_anchor_button"
                        ) {
                            showContextAnchorDialog = true
                        }

                        UtilityChip(
                            icon = Icons.Default.Language,
                            label = "مخزن Chrome (${vaultEntries.size})",
                            tint = NeuralCyan,
                            testTag = "chrome_vault_button"
                        ) {
                            ChromeVaultBridge.openInChromeVault(
                                context = context,
                                books = books,
                                knowledge = vaultEntries,
                                hungerReports = hungerReports,
                                vaultToken = workspace?.chromeVaultToken ?: "default"
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Main Message Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty() && streamingText == null) {
                        WelcomeHeroState(
                            vaultCount = vaultEntries.size,
                            onQuickPrompt = { prompt ->
                                inputText = prompt
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .align(Alignment.TopCenter),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                AnimatedMessageReveal {
                                    ChatMessageBubble(
                                        message = msg,
                                        onCopyCode = { code ->
                                            clipboard.setText(AnnotatedString(code))
                                            scope.launch { snackbarHostState.showSnackbar("تم نسخ الكود بنجاح") }
                                        },
                                        onPreviewCode = { code, lang ->
                                            previewCodePair = code to lang
                                        },
                                        onViewHungerTxt = { name, content ->
                                            txtViewerPair = name to content
                                        },
                                        onShareHungerTxt = { name, content ->
                                            scope.launch {
                                                val uri = ChromeVaultBridge.saveAndShareTxtFile(context, name, content)
                                                ChromeVaultBridge.shareTxtFile(context, uri, name)
                                            }
                                        },
                                        onOpenSourceUrl = { url ->
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (_: Exception) {
                                            }
                                        },
                                        onMarketingResponse = { accept ->
                                            viewModel.respondToMarketingSearch(msg, accept)
                                        }
                                    )
                                }
                            }

                            if (isGenerating) {
                                item(key = "streaming_bubble") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AnimatedAiThinkingBar(phaseLabel = activePhaseLabel)
                                        if (!streamingText.isNullOrBlank()) {
                                            GlowingStreamingContainer {
                                                Text(
                                                    text = streamingText.orEmpty(),
                                                    color = TextPrimary,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pinned Pending Attachments Bar ALWAYS visible directly ABOVE the chat box
                AnimatedVisibility(
                    visible = pendingAttachments.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    PendingAttachmentsBar(
                        attachments = pendingAttachments,
                        onRemove = viewModel::removeAttachment
                    )
                }

                // Expandable 3-Button Plus Menu (Images, Files, Live Camera)
                AnimatedVisibility(
                    visible = plusMenuExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlusOptionCard(
                            icon = Icons.Default.Image,
                            label = stringResource(id = R.string.btn_upload_images),
                            accent = LiquidGold,
                            testTag = "attach_images_button",
                            modifier = Modifier.weight(1f)
                        ) {
                            plusMenuExpanded = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        PlusOptionCard(
                            icon = Icons.Default.AttachFile,
                            label = stringResource(id = R.string.btn_upload_files),
                            accent = NeuralCyan,
                            testTag = "attach_files_button",
                            modifier = Modifier.weight(1f)
                        ) {
                            plusMenuExpanded = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                        PlusOptionCard(
                            icon = Icons.Default.CameraAlt,
                            label = stringResource(id = R.string.btn_live_camera),
                            accent = EmeraldVerified,
                            testTag = "attach_camera_button",
                            modifier = Modifier.weight(1f)
                        ) {
                            plusMenuExpanded = false
                            showCameraModal = true
                        }
                    }
                }

                // Bottom Input Dock
                Surface(
                    color = ObsidianSurface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGlass)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Deep Search Toggle Button
                            IconButton(
                                onClick = viewModel::toggleDeepSearch,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (deepSearchEnabled) NeuralCyan.copy(alpha = 0.22f) else ObsidianCard
                                    )
                                    .border(
                                        1.2.dp,
                                        if (deepSearchEnabled) NeuralCyan else BorderGlass,
                                        CircleShape
                                    )
                                    .testTag("deep_search_toggle")
                            ) {
                                Icon(
                                    Icons.Default.TravelExplore,
                                    contentDescription = stringResource(id = R.string.btn_deep_search),
                                    tint = if (deepSearchEnabled) NeuralCyan else TextSecondary
                                )
                            }

                            // Plus (+) Button right next to Search
                            IconButton(
                                onClick = { plusMenuExpanded = !plusMenuExpanded },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (plusMenuExpanded) LiquidGold else ObsidianCard)
                                    .border(1.dp, LiquidGold.copy(alpha = 0.6f), CircleShape)
                                    .testTag("plus_attachments_button")
                            ) {
                                Icon(
                                    imageVector = if (plusMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "إضافة مرفقات",
                                    tint = if (plusMenuExpanded) ObsidianBlack else LiquidGold
                                )
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = if (deepSearchEnabled) {
                                            "البحث العميق مفعّل — اكتب سؤالك..."
                                        } else {
                                            "اسأل، ابرمج، أرسل رابط فيديو، أو اكتب طلبك..."
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = TextMuted
                                    )
                                },
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LiquidGold,
                                    unfocusedBorderColor = BorderGlass,
                                    focusedContainerColor = ObsidianCard,
                                    unfocusedContainerColor = ObsidianCard,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input")
                            )

                            // Live Voice + Emotion Mic Button
                            IconButton(
                                onClick = { showLiveVoice = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianCard)
                                    .border(1.dp, VioletPulse.copy(alpha = 0.7f), CircleShape)
                                    .testTag("live_voice_button")
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "محادثة صوتية حية",
                                    tint = VioletPulse
                                )
                            }

                            // Send Button
                            IconButton(
                                onClick = {
                                    val msgToSend = inputText
                                    inputText = ""
                                    plusMenuExpanded = false
                                    viewModel.sendUserMessage(msgToSend)
                                },
                                enabled = !isGenerating && (inputText.isNotBlank() || pendingAttachments.isNotEmpty()),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(LiquidGold)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = ObsidianBlack
                                )
                            }
                        }

                        if (deepSearchEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "● البحث العميق نشط: سيتحقق المحرك من جميع مصادر الويب 100% قبل الرد",
                                color = NeuralCyan,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCameraModal) {
        InAppCameraModal(
            onCaptured = { bmp ->
                viewModel.addAttachments(listOf(AttachmentProcessor.processCapturedBitmap(bmp)))
                showCameraModal = false
            },
            onDismiss = { showCameraModal = false }
        )
    }

    if (showLiveVoice) {
        LiveVoiceEmotionOverlay(
            detectedEmotion = detectedEmotion,
            isProcessing = isGenerating,
            lastReply = lastLiveReply,
            onSpokenResult = { spoken -> viewModel.processLiveVoiceInput(spoken) },
            onDismiss = { showLiveVoice = false }
        )
    }

    previewCodePair?.let { (code, lang) ->
        LiveCodePreviewDialog(
            code = code,
            language = lang,
            onDismiss = { previewCodePair = null }
        )
    }

    txtViewerPair?.let { (filename, content) ->
        AlertDialog(
            onDismissRequest = { txtViewerPair = null },
            containerColor = ObsidianSurface,
            title = { Text(filename, color = LiquidGold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBlack)
                        .padding(12.dp)
                ) {
                    Text(
                        text = content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val uri = ChromeVaultBridge.saveAndShareTxtFile(context, filename, content)
                        ChromeVaultBridge.shareTxtFile(context, uri, filename)
                    }
                }) {
                    Text("حفظ ومشاركة TXT")
                }
            },
            dismissButton = {
                TextButton(onClick = { txtViewerPair = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    if (showContextAnchorDialog) {
        var anchorDraft by remember(workspace?.pinnedContextAnchor) {
            mutableStateOf(workspace?.pinnedContextAnchor.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showContextAnchorDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("الذاكرة المثبتة طويلة المدى (منع التشتيت)", color = LiquidGold) },
            text = {
                Column {
                    Text(
                        text = "اكتب الهدف العام أو التعليمات الدائمة ليتذكرها النظام مهما طال الحوار دون أن يتشتت أبداً:",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = anchorDraft,
                        onValueChange = { anchorDraft = it },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updatePinnedAnchor(anchorDraft)
                    showContextAnchorDialog = false
                }) {
                    Text("حفظ في السحابة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContextAnchorDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun WelcomeHeroState(
    vaultCount: Int,
    onQuickPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NexusNeuralOrb(isActive = false, size = 132.dp)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(id = R.string.welcome_headline),
            style = MaterialTheme.typography.headlineMedium,
            color = LiquidGold,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.welcome_subheadline),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(ObsidianCard)
                .border(1.dp, BorderGlass, RoundedCornerShape(99.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "الذاكرة الدائمة: $vaultCount وحدة معرفية • Boosthis API نشط",
                color = NeuralCyan,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "صمم لي صفحة هبوط فخمة بـ HTML مع انيميشن",
                "كله اشتغل تمام",
                "ابحث في هذا الفيديو بالوقت: ",
                "The Hunger: ما المعلومات التي تنقصك؟"
            ).forEach { sample ->
                Surface(
                    onClick = { onQuickPrompt(sample) },
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                ) {
                    Text(
                        text = sample,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentsBar(
    attachments: List<PendingAttachment>,
    onRemove: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianElevated)
            .border(1.dp, LiquidGold.copy(alpha = 0.45f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attachments.forEach { att ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCard)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                    .padding(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (att.bitmap != null) {
                        Image(
                            bitmap = att.bitmap.asImageBitmap(),
                            contentDescription = att.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else if (att.uri != null && att.kind == AttachmentKind.IMAGE) {
                        AsyncImage(
                            model = att.uri,
                            contentDescription = att.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = att.displayName,
                            tint = NeuralCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = att.displayName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                    IconButton(
                        onClick = { onRemove(att.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إزالة المرفق",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onCopyCode: (String) -> Unit,
    onPreviewCode: (String, String) -> Unit,
    onViewHungerTxt: (String, String) -> Unit,
    onShareHungerTxt: (String, String) -> Unit,
    onOpenSourceUrl: (String) -> Unit,
    onMarketingResponse: (Boolean) -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(Color(0xFF1F2A44), Color(0xFF172036)))
                    } else {
                        Brush.linearGradient(listOf(ObsidianCard, ObsidianSurface))
                    }
                )
                .border(
                    1.dp,
                    if (isUser) LiquidGold.copy(alpha = 0.55f) else BorderGlass,
                    RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Status Badges Row
                if (message.deepSearchUsed || message.boosthisVerified || !message.emotionTag.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.deepSearchUsed) {
                            BadgePill("البحث العميق المحقق 100%", NeuralCyan)
                        }
                        if (message.boosthisVerified) {
                            BadgePill("Boosthis Verified ✓", EmeraldVerified)
                        }
                        message.emotionTag?.let { tag ->
                            BadgePill(tag, VioletPulse)
                        }
                    }
                }

                // Attachment names
                message.attachmentNames?.takeIf { it.isNotEmpty() }?.let { names ->
                    Text(
                        text = "📎 مرفقات: ${names.joinToString("، ")}",
                        color = LiquidGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Text(
                    text = message.content,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Code Block Card with Copy + Preview buttons side by side
                val code = message.codeBlock
                if (!code.isNullOrBlank()) {
                    val lang = message.codeLanguage ?: "code"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianBlack)
                            .border(1.dp, LiquidGold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianElevated)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = EmeraldVerified,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = lang.uppercase(),
                                    color = LiquidGold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onCopyCode(code) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("copy_code_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(id = R.string.btn_copy), style = MaterialTheme.typography.labelLarge)
                                }
                                Button(
                                    onClick = { onPreviewCode(code, lang) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeuralCyan, contentColor = ObsidianBlack),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("preview_code_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "معاينة", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(id = R.string.btn_preview),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = code.take(650) + if (code.length > 650) "\n... (اضغط معاينة لعرض الكود كاملاً)" else "",
                            color = TextSecondary,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Hunger .txt file attachment card
                if (!message.hungerTxtFilename.isNullOrBlank() && !message.hungerTxtContent.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianElevated)
                            .border(1.dp, Color(0xFFFF8A47), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFFF8A47))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.hungerTxtFilename,
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onViewHungerTxt(message.hungerTxtFilename, message.hungerTxtContent)
                            }) {
                                Text("عرض", color = LiquidGold)
                            }
                            IconButton(
                                onClick = {
                                    onShareHungerTxt(message.hungerTxtFilename, message.hungerTxtContent)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "تحميل ومشاركة TXT", tint = EmeraldVerified)
                            }
                        }
                    }
                }

                // Marketing Yes / No Action Prompt
                if (message.marketingPromptPending) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onMarketingResponse(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified, contentColor = ObsidianBlack),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("marketing_yes_button")
                        ) {
                            Text("نعم — ابحث عن عملاء", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onMarketingResponse(false) },
                            modifier = Modifier
                                .weight(0.6f)
                                .height(48.dp)
                                .testTag("marketing_no_button")
                        ) {
                            Text("لا", color = TextSecondary)
                        }
                    }
                }

                // Verified clickable web sources
                message.sources?.takeIf { it.isNotEmpty() }?.let { srcList ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "المصادر والروابط المحققة:",
                            color = NeuralCyan,
                            style = MaterialTheme.typography.labelMedium
                        )
                        srcList.take(6).forEach { entry ->
                            val urlPart = entry.substringAfterLast(" — ").trim()
                            Text(
                                text = "• $entry",
                                color = NeuralCyan,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clickable {
                                        if (urlPart.startsWith("http")) onOpenSourceUrl(urlPart)
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(99.dp),
        color = ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
        modifier = Modifier
            .height(38.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlusOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
        modifier = modifier
            .height(64.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.8.dp, color.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
