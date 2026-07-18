package com.example.ui.servercreation.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.servercreation.CreateServerDraft
import com.example.ui.servercreation.WizardTheme
import com.example.ui.servercreation.components.VersionArtwork
import com.example.ui.servercreation.components.WizardInfoBanner

@Composable
fun VersionStep(
    draft: CreateServerDraft,
    onDraftUpdate: (CreateServerDraft) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 for Recommended, 1 for Manual

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                "Server Version",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Text(
                "Choose a recommended version or select manually.",
                style = MaterialTheme.typography.bodySmall,
                color = WizardTheme.SecondaryText
            )
        }

        // Tab Row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF1F4F9)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                TabButton(
                    text = "Recommended",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    hasStar = true
                )
                TabButton(
                    text = "Manual",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Recommended Content
        if (selectedTab == 0) {
            RecommendedVersionCard()
        } else {
            // Manual Content (Placeholder)
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Manual version selection coming soon", color = WizardTheme.SecondaryText)
            }
        }

        // Other Versions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VersionRow("1.20.80", "Stable", "Jun 13, 2024", true)
            VersionRow("1.20.71", "Stable", "May 23, 2024", false)
            VersionRow("1.20.60", "Stable", "Apr 25, 2024", false)
            VersionRow("1.19 LTS", "Long Term Support", "Jun 7, 2023", false)
        }

        WizardInfoBanner(
            text = "Version sync coming in a future runtime update. You will be able to auto-sync to the latest version."
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasStar: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                listOf(WizardTheme.GradientLeft, WizardTheme.GradientRight)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else Modifier
                )
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasStar) {
                    Icon(
                        Icons.Default.Stars,
                        contentDescription = null,
                        tint = if (selected) Color.White else WizardTheme.SecondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else WizardTheme.SecondaryText
                    )
                )
            }
        }
    }
}

@Composable
private fun RecommendedVersionCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WizardTheme.OptionCardRadius),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        listOf(WizardTheme.SelectedCardGradientLeft, WizardTheme.SelectedCardGradientRight)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(WizardTheme.GradientLeft, WizardTheme.GradientRight)
                    ),
                    shape = RoundedCornerShape(WizardTheme.OptionCardRadius)
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon (Minecraft block)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WizardTheme.SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    VersionArtwork(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Latest Stable",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = WizardTheme.PrimaryBlue
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFF1EEFF),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Recommended",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = WizardTheme.AccentPurple,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Text(
                        "1.20.80  •  Stable Release",
                        style = MaterialTheme.typography.bodySmall,
                        color = WizardTheme.SecondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        "Best stability and performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WizardTheme.SecondaryText,
                        fontSize = 12.sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(WizardTheme.PrimaryBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: String,
    status: String,
    date: String,
    selected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WizardTheme.OptionCardRadius),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, WizardTheme.Border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        version,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = WizardTheme.PrimaryText
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(6.dp).background(WizardTheme.Success, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        status, 
                        style = MaterialTheme.typography.labelSmall, 
                        color = WizardTheme.SecondaryText,
                        fontSize = 11.sp
                    )
                }
                Text(
                    "Released $date", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = WizardTheme.SecondaryText,
                    fontSize = 11.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, WizardTheme.Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(WizardTheme.PrimaryBlue, CircleShape)
                    )
                }
            }
        }
    }
}
