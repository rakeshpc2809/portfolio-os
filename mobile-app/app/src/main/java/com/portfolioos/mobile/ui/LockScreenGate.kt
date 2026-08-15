package com.portfolioos.mobile.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .background(Color(0xFF0F172A)), // Obsidian dark
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSecurityEnrolled) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = Color(0xFFA3E635), // Electric Lime
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "PORTFOLIO OS LOCKED",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Biometric or device PIN authentication required to view financial portfolio data.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onAuthenticate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA3E635)),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Unlock App",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Device Security Required",
                        tint = Color(0xFFF59E0B), // Amber Warning
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Device Security Required",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Portfolio OS contains sensitive financial data. Please set up a PIN, pattern, or biometric lock in Android Settings.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open Security Settings",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRecheckSecurity,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Check Again",
                            color = Color(0xFF38BDF8), // Neon Cyan
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
