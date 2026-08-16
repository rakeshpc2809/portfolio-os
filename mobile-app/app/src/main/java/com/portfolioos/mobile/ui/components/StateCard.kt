package com.portfolioos.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.SpacingTokens
import com.portfolioos.mobile.ui.theme.TypographyTokens

@Composable
fun PortfolioStateCard(
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color = iconTint.copy(alpha = 0.15f),
    title: String,
    subtitle: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
        shape = ShapeTokens.GlassCardShape, // Aligned to web 16.dp standard
        border = BorderStroke(1.dp, ColorTokens.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SpacingTokens.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = iconBgColor,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = TypographyTokens.CardTitle
                    )
                    Text(
                        text = subtitle,
                        style = TypographyTokens.BodyText.copy(
                            color = iconTint,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(SpacingTokens.md))
            Text(
                text = description,
                style = TypographyTokens.BodyText
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(SpacingTokens.lg))
                OutlinedButton(
                    onClick = onAction,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = iconTint),
                    shape = ShapeTokens.PillShape,
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = actionLabel,
                        style = TypographyTokens.BadgeTag.copy(color = iconTint)
                    )
                }
            }
        }
    }
}
