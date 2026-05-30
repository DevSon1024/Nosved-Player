package com.devson.nvplayer.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateTrigger = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Hero commitment Card
                PrivacyHeroCard()

                // Policy Sections
                AnimatedVisibility(
                    visible = animateTrigger,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        
                        // Section 1: Data Collection & Off-line Operation
                        PolicySection(
                            title = "DATA MINIMIZATION & OFFLINE SECURITY",
                            icon = Icons.Default.Shield
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.Lock,
                                title = "Zero Personal Data Collection",
                                description = "Nosved Player does not collect, record, transmit, or share any personal information, usage stats, or telemetry."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.Security,
                                title = "100% Offline Processing",
                                description = "The app does not access the internet. All playback operations, file indexing, and configurations run entirely on your device."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "No Sign-Up or Accounts",
                                description = "You do not need to create an account or provide any login credentials to use all features of the application."
                            )
                        }

                        // Section 2: Local Playback Data
                        PolicySection(
                            title = "PLAYBACK HISTORY & USER DATA",
                            icon = Icons.Default.History
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.Lock,
                                title = "Device-Bound History",
                                description = "Your watch progress and general application preferences are stored only inside local databases and secure app preferences. No backup or server storage exists."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "Complete User Autonomy",
                                description = "You can purge your playback history and clear app cache anytime. Uninstalling the app completely deletes all local configuration databases from your device."
                            )
                        }

                        // Section 3: Third Party & Integration
                        PolicySection(
                            title = "THIRD-PARTY SERVICES & ANALYTICS",
                            icon = Icons.Default.Security
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.Shield,
                                title = "Zero Tracking & Ads",
                                description = "We do not integrate Google Analytics, Firebase SDKs, crash report trackers, or advertising SDKs. The codebase relies solely on open-source, standard playback libraries."
                            )
                        }

                        // Section 4: Child Safety & Updates
                        PolicySection(
                            title = "CHILD ACCESSIBILITY & POLICY UPDATES",
                            icon = Icons.Default.Info
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "Fully Family Safe",
                                description = "With absolutely no tracking or profiling, our application is safe for all age groups, conforming fully to children's privacy standards."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.Info,
                                title = "Transparent Updates",
                                description = "Any future updates to this policy will be clearly versioned and presented in-app. Continued use constitutes awareness of any updated terms."
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Footer
                        Text(
                            text = "Nosved Player\nCommitted to Open-Source and Privacy\nCreated by DevSon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant gradient icon backplate
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Text(
                text = "Privacy Commitment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Nosved Player is built from the ground up to respect your privacy. Everything is processed entirely offline on your device, ensuring maximum security and autonomy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                PrivacyBadge(text = "100% Offline")
                PrivacyBadge(text = "Zero Trackers")
                PrivacyBadge(text = "Open Source")
            }
        }
    }
}

@Composable
private fun PrivacyBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun PolicySection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Section Cards Content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun PolicyPointRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PolicyPointSeparator() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
