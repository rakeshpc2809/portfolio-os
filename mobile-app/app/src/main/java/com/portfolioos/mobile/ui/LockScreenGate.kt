package com.portfolioos.mobile.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.SpacingTokens
import com.portfolioos.mobile.ui.theme.TypographyTokens

@Composable
fun LockScreenGate(
    isSecurityEnrolled: Boolean,
    onAuthenticate: () -> Unit,
    onRecheckSecurity: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorTokens.ObsidianBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
            shape = ShapeTokens.GlassCardShape,
            border = BorderStroke(1.dp, ColorTokens.CardBorder),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(SpacingTokens.xxl)
        ) {
            Column(
                modifier = Modifier.padding(SpacingTokens.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSecurityEnrolled) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = ColorTokens.ElectricLime,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.lg))
                    Text(
                        text = "PORTFOLIO OS LOCKED",
                        style = TypographyTokens.CardTitle.copy(
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Biometric or device PIN authentication required to view financial portfolio data.",
                        style = TypographyTokens.BodyText.copy(textAlign = TextAlign.Center)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.xxl))
                    Button(
                        onClick = onAuthenticate,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.ElectricLime),
                        shape = ShapeTokens.PillShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Unlock App",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.ObsidianBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Device Security Required",
                        tint = ColorTokens.AmberWarning,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.lg))
                    Text(
                        text = "Device Security Required",
                        style = TypographyTokens.CardTitle.copy(fontSize = 18.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Portfolio OS contains sensitive financial data. Please set up a PIN, pattern, or biometric lock in Android Settings.",
                        style = TypographyTokens.BodyText.copy(textAlign = TextAlign.Center)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.xxl))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.AmberWarning),
                        shape = ShapeTokens.PillShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open Security Settings",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.ObsidianBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(SpacingTokens.sm))
                    OutlinedButton(
                        onClick = onRecheckSecurity,
                        shape = ShapeTokens.PillShape,
                        border = BorderStroke(1.dp, ColorTokens.CyanSky.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Check Again",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.CyanSky,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
