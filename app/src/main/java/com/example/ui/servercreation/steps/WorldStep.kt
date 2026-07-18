package com.example.ui.servercreation.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.servercreation.*
import com.example.ui.servercreation.components.WizardInfoBanner
import com.example.ui.servercreation.components.WorldArtwork

@Composable
fun WorldStep(
    draft: CreateServerDraft,
    onDraftUpdate: (CreateServerDraft) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                "World Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Text(
                "Configure the world your players will explore.",
                style = MaterialTheme.typography.bodySmall,
                color = WizardTheme.SecondaryText
            )
        }

        // World Type
        Column {
            Text(
                "World Type",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(WorldType.entries) { type ->
                    WorldTypeCard(
                        type = type,
                        selected = draft.worldType == type,
                        onClick = { onDraftUpdate(draft.copy(worldType = type)) }
                    )
                }
            }
        }

        // Difficulty
        Column {
            Text(
                "Difficulty",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF1F4F9)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Difficulty.entries.forEach { diff ->
                        DifficultyButton(
                            diff = diff,
                            selected = draft.difficulty == diff,
                            onClick = { onDraftUpdate(draft.copy(difficulty = diff)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Seed
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Seed",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = WizardTheme.PrimaryText
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "(optional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = WizardTheme.SecondaryText
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.seed,
                onValueChange = { onDraftUpdate(draft.copy(seed = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 123456789 or leave blank for random", color = WizardTheme.SecondaryText.copy(alpha = 0.5f)) },
                shape = RoundedCornerShape(WizardTheme.InputRadius),
                leadingIcon = {
                    Icon(Icons.Outlined.Public, contentDescription = null, tint = WizardTheme.PrimaryBlue)
                },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Casino, 
                        contentDescription = "Randomize", 
                        tint = WizardTheme.PrimaryBlue,
                        modifier = Modifier.clickable { /* Randomize logic if any */ }
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WizardTheme.PrimaryBlue,
                    unfocusedBorderColor = WizardTheme.Border,
                    focusedContainerColor = WizardTheme.SoftBlue.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )
        }

        // World Name
        Column {
            Text(
                "World Name",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.worldName,
                onValueChange = { onDraftUpdate(draft.copy(worldName = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. My Awesome World", color = WizardTheme.SecondaryText.copy(alpha = 0.5f)) },
                shape = RoundedCornerShape(WizardTheme.InputRadius),
                leadingIcon = {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = WizardTheme.PrimaryBlue)
                },
                trailingIcon = {
                    Text(
                        "${draft.worldName.length}/32",
                        style = MaterialTheme.typography.bodySmall,
                        color = WizardTheme.SecondaryText.copy(alpha = 0.5f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WizardTheme.PrimaryBlue,
                    unfocusedBorderColor = WizardTheme.Border,
                    focusedContainerColor = WizardTheme.SoftBlue.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )
        }

        WizardInfoBanner(
            text = "These settings affect world generation and can't be changed after the world is created."
        )
    }
}

@Composable
private fun WorldTypeCard(
    type: WorldType,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(WizardTheme.OptionCardRadius),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color.Transparent else WizardTheme.Border
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = if (selected) {
                        Brush.linearGradient(
                            listOf(WizardTheme.SelectedCardGradientLeft, WizardTheme.SelectedCardGradientRight)
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.White, Color.White))
                    }
                )
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(WizardTheme.GradientLeft, WizardTheme.GradientRight)
                            ),
                            shape = RoundedCornerShape(WizardTheme.OptionCardRadius)
                        )
                    } else Modifier
                )
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    WorldArtwork(
                        worldType = type.name,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                    
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(WizardTheme.PrimaryBlue, CircleShape)
                                    .padding(2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (selected) WizardTheme.PrimaryBlue else WizardTheme.PrimaryText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    type.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = WizardTheme.SecondaryText,
                    maxLines = 3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DifficultyButton(
    diff: Difficulty,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                diff.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) WizardTheme.PrimaryBlue else WizardTheme.SecondaryText
                )
            )
        }
    }
}
