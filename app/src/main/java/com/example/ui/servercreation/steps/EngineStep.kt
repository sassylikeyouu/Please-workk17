package com.example.ui.servercreation.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.server.template.TemplateRegistry
import com.example.ui.servercreation.CreateServerDraft
import com.example.ui.servercreation.WizardTheme
import com.example.ui.servercreation.components.EngineArtwork
import com.example.ui.servercreation.components.SelectableOptionCard
import com.example.ui.servercreation.components.WizardInfoBanner

@Composable
fun EngineStep(
    draft: CreateServerDraft,
    onDraftUpdate: (CreateServerDraft) -> Unit
) {
    val templates = TemplateRegistry.ALL_TEMPLATES

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                "Choose Server Engine",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Text(
                "Select the engine software that will run your server.",
                style = MaterialTheme.typography.bodySmall,
                color = WizardTheme.SecondaryText
            )
        }

        templates.forEach { template ->
            SelectableOptionCard(
                title = template.name,
                description = template.description,
                selected = draft.engine?.id == template.id,
                onClick = { onDraftUpdate(draft.copy(engine = template)) },
                icon = {
                    EngineArtwork(
                        engineId = template.id,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        }

        WizardInfoBanner(
            text = "Each engine has different performance, compatibility, and plugin support. You can change this later."
        )
    }
}
