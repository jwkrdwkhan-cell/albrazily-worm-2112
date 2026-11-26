package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.firebase.NexusRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.signOutUser
import com.example.service.GeminiEngineService
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatStudioScreen
import com.example.ui.screens.TrainingStudioScreen
import com.example.ui.theme.NexusAITheme
import com.example.viewmodel.AppMode
import com.example.viewmodel.NexusViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            NexusAITheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: "Nexus Commander"
        val email = user.email

        key(uid) {
            val viewModel: NexusViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        NexusViewModel(
                            repository = NexusRepository(app.applicationContext),
                            gemini = GeminiEngineService(),
                            currentUserId = uid,
                            userDisplayName = displayName,
                            userEmail = email
                        )
                    }
                }
            )
            AuthenticatedNexusRoot(viewModel = viewModel)
        }
    }
}

@Composable
private fun AuthenticatedNexusRoot(viewModel: NexusViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val appMode by viewModel.appMode.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = appMode,
        transitionSpec = {
            if (targetState == AppMode.TRAINING_STUDIO) {
                (slideInHorizontally(tween(340)) { -it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(340)) { it / 3 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(340)) { it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(340)) { -it / 3 } + fadeOut(tween(220)))
            }
        },
        label = "app_mode_transition"
    ) { mode ->
        when (mode) {
            AppMode.CHAT_STUDIO -> ChatStudioScreen(
                viewModel = viewModel,
                onOpenTrainingStudio = { viewModel.switchMode(AppMode.TRAINING_STUDIO) },
                onSignOut = {
                    signOutUser(credentialManager, scope)
                }
            )

            AppMode.TRAINING_STUDIO -> TrainingStudioScreen(
                viewModel = viewModel,
                onBackToChat = { viewModel.switchMode(AppMode.CHAT_STUDIO) }
            )
        }
    }
}
