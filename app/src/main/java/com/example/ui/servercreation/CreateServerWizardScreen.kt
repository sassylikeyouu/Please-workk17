package com.example.ui.servercreation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainViewModel
import com.example.ui.components.MineHostBrandHeader
import com.example.ui.servercreation.components.WizardBottomBar
import com.example.ui.servercreation.components.WizardCard
import com.example.ui.servercreation.components.WizardProgressIndicator
import com.example.ui.servercreation.steps.*

@Composable
fun CreateServerWizardScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val wizardViewModel: CreateServerWizardViewModel = viewModel()
    val draft by wizardViewModel.draft.collectAsState()
    val currentStep by wizardViewModel.currentStep.collectAsState()
    val canContinue by wizardViewModel.canContinue.collectAsState()

    BackHandler {
        if (!wizardViewModel.previousStep()) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(WizardTheme.Background)
                    .statusBarsPadding()
            ) {
                MineHostBrandHeader(
                    showBack = true,
                    onBack = {
                        if (!wizardViewModel.previousStep()) {
                            onBack()
                        }
                    },
                    compact = true,
                    showProfile = false
                )
                
                Column(modifier = Modifier.padding(horizontal = WizardTheme.HorizontalPadding)) {
                    Spacer(Modifier.height(WizardTheme.HeaderToTitle))
                    Text(
                        "Create New Server",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = WizardTheme.PrimaryText,
                            fontSize = 28.sp
                        )
                    )
                    Spacer(Modifier.height(WizardTheme.TitleToSubtitle))
                    Text(
                        "Step ${currentStep.ordinal + 1} of 7 · ${currentStep.title}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = WizardTheme.PrimaryBlue,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(Modifier.height(WizardTheme.SubtitleToProgress))
                    WizardProgressIndicator(currentStep = currentStep)
                    Spacer(Modifier.height(WizardTheme.ProgressToMainCard))
                }
            }
        },
        bottomBar = {
            WizardBottomBar(
                onBack = {
                    if (!wizardViewModel.previousStep()) {
                        onBack()
                    }
                },
                onContinue = {
                    if (currentStep == WizardStep.REVIEW) {
                        wizardViewModel.createServer(mainViewModel, onDone)
                    } else {
                        wizardViewModel.nextStep()
                    }
                },
                backEnabled = currentStep.ordinal > 0,
                continueEnabled = canContinue,
                isLastStep = currentStep == WizardStep.REVIEW
            )
        },
        containerColor = WizardTheme.Background
    ) { padding ->
        androidx.compose.runtime.key(currentStep) {
            val scrollState = rememberScrollState()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = WizardTheme.HorizontalPadding)
            ) {
                WizardCard {
                    when (currentStep) {
                        WizardStep.BASICS -> BasicsStep(draft, wizardViewModel::setDraft)
                        WizardStep.ENGINE -> EngineStep(draft, wizardViewModel::setDraft)
                        WizardStep.VERSION -> VersionStep(draft, wizardViewModel::setDraft)
                        WizardStep.WORLD -> WorldStep(draft, wizardViewModel::setDraft)
                        WizardStep.PERFORMANCE -> PerformanceStep(draft, wizardViewModel::setDraft)
                        WizardStep.NETWORK -> NetworkStep(draft, wizardViewModel::setDraft)
                        WizardStep.REVIEW -> ReviewStep(draft, wizardViewModel::setDraft)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
