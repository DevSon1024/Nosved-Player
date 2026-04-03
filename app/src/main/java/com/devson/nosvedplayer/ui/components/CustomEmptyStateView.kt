package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A premium, animated empty-state view reusable across VideoListScreen
 * and FolderListScreen (and anywhere else the list may be empty).
 *
 * @param icon         Material icon to display as the central illustration.
 * @param heading      Bold headline, e.g. "No Videos Found".
 * @param subtext      Descriptive helper text below the headline.
 * @param ctaLabel     Label for the primary call-to-action button.
 * @param onCtaClick   Action triggered by the button.
 * @param modifier     Outer layout modifier.
 */
@Composable
fun CustomEmptyStateView(
    icon: ImageVector = Icons.Filled.VideoLibrary,
    heading: String = "No Videos Found",
    subtext: String = "We couldn't find any video files on your device.",
    ctaLabel: String = "Scan Device for Videos",
    onCtaClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Entrance pop animation
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMedium
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        //  Illustration cluster 
        Box(
            modifier = Modifier
                .scale(scale.value)
                .size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glowing ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush  = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        shape  = CircleShape
                    )
            )
            // Mid ring
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        color  = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape  = CircleShape
                    )
            )
            // Icon
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(44.dp)
            )

            // Small accent dot - top-right corner
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        //  Heading 
        Text(
            text       = heading,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
            textAlign  = TextAlign.Center,
            letterSpacing = (-0.3).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        //  Sub-text 
        Text(
            text      = subtext,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        //  Primary CTA 
        Button(
            onClick  = onCtaClick,
            shape    = RoundedCornerShape(6.dp),          // sharp - matches NosvedShapes
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector        = Icons.Filled.Search,
                contentDescription = null,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text       = ctaLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp
            )
        }
    }
}
