package com.example.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.MineHostBrandHeader
import com.example.ui.components.MineHostPageTitle
import com.example.ui.components.SectionTitle
import com.example.ui.components.SettingsRow
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BlueSoft
import com.example.ui.theme.GreenSoft
import com.example.ui.theme.MineHostBackgroundBottom
import com.example.ui.theme.MineHostBackgroundTop
import com.example.ui.theme.MineHostBlue
import com.example.ui.theme.MineHostDivider
import com.example.ui.theme.MineHostGreen
import com.example.ui.theme.MineHostOrange
import com.example.ui.theme.MineHostPurple
import com.example.ui.theme.MineHostSurfaceVariant
import com.example.ui.theme.MineHostTextSecondary
import com.example.ui.theme.OrangeSoft
import com.example.ui.theme.PurpleSoft

@Composable
fun AccountScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MineHostBackgroundTop, MineHostBackgroundBottom)
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        MineHostBrandHeader(showBack = true, onBack = onBack, compact = true)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MineHostPageTitle(
                title = "Account",
                subtitle = "Manage your local profile and future account integrations."
            )
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MineHostBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MineHostBlue,
                            modifier = Modifier.fillMaxSize(0.7f)
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Local user", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "On-device profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MineHostTextSecondary
                        )
                        Spacer(Modifier.size(8.dp))
                        StatusBadge(
                            text = "No cloud account",
                            color = MineHostTextSecondary,
                            background = MineHostSurfaceVariant,
                            showDot = false
                        )
                    }
                }
            }

            SectionTitle("Profile & Contact")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsRow(
                        "Edit local profile",
                        "Update display preferences stored on this device",
                        Icons.Outlined.Person,
                        MineHostPurple,
                        PurpleSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "Email address",
                        "Account service is not configured",
                        Icons.Outlined.Email,
                        MineHostBlue,
                        BlueSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "Phone number",
                        "Not collected by this local build",
                        Icons.Outlined.Phone,
                        MineHostGreen,
                        GreenSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "App access",
                        "Protected by Android app sandbox",
                        Icons.Outlined.Lock,
                        MineHostOrange,
                        OrangeSoft
                    )
                }
            }

            SectionTitle("Account & Security")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsRow(
                        "Linked accounts",
                        "Google and Discord linking require a backend service",
                        Icons.Outlined.Link,
                        MineHostPurple,
                        PurpleSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "Subscription plan",
                        "No subscription service is connected",
                        Icons.Outlined.WorkspacePremium,
                        MineHostBlue,
                        BlueSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "Billing",
                        "No payment information is stored",
                        Icons.Outlined.CreditCard,
                        MineHostGreen,
                        GreenSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "Privacy",
                        "All current app data is local to this device",
                        Icons.Outlined.PrivacyTip,
                        MineHostPurple,
                        PurpleSoft
                    )
                    HorizontalDivider(color = MineHostDivider)
                    SettingsRow(
                        "Connected devices",
                        "Remote account sessions are unavailable",
                        Icons.Outlined.Devices,
                        MineHostBlue,
                        BlueSoft
                    )
                }
            }
            Spacer(Modifier.size(22.dp))
        }
    }
}
