package com.portfolioos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorView(holdings: List<FlatHoldingDto>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedIsin by remember { mutableStateOf(holdings.firstOrNull()?.isin ?: "") }
    var selectedName by remember { mutableStateOf(holdings.firstOrNull()?.fundName ?: "Select Scheme") }
    var unitsText by remember { mutableStateOf("100.0") }
    var priceText by remember { mutableStateOf("150.0") }
    var tradeType by remember { mutableStateOf("DISPOSAL") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "⚡ WHAT-IF TRADE SIMULATOR",
            color = Color(0xFFD0FF00),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "Preview tax drag and post-trade XIRR before executing trades.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scheme Selector
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = !isExpanded }
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Scheme", color = Color(0xFF94A3B8)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00F0FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                holdings.forEach { holding ->
                    DropdownMenuItem(
                        text = { Text(holding.fundName) },
                        onClick = {
                            selectedIsin = holding.isin
                            selectedName = holding.fundName
                            isExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = unitsText,
                onValueChange = { unitsText = it },
                label = { Text("Units", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price/NAV (₹)", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tradeType == "DISPOSAL",
                onClick = { tradeType = "DISPOSAL" },
                label = { Text("Simulate Sale (Disposal)") }
            )
            FilterChip(
                selected = tradeType == "ACQUISITION",
                onClick = { tradeType = "ACQUISITION" },
                label = { Text("Simulate Buy (SIP)") }
            )
        }

        Button(
            onClick = {
                val units = unitsText.toDoubleOrNull()
                val price = priceText.toDoubleOrNull()
                if (units == null || units <= 0.0 || price == null || price <= 0.0) {
                    resultText = "⚠️ Validation Error: Please enter valid positive numbers for Units and Price/NAV."
                    return@Button
                }
                isLoading = true
                scope.launch {
                    try {
                        val req = TradeSimulationRequestDto(
                            isin = selectedIsin,
                            schemeName = selectedName,
                            units = units,
                            pricePerUnit = price,
                            tradeType = tradeType
                        )
                        val res = SyncApiClient.simulateTradeWithFallback(context, req)
                        resultText = """
                            ✓ Simulation Execution Successful (Live Engine)
                            • Target: ${res.schemeName}
                            • Trade Type: ${res.tradeType} (${res.units} Units @ ₹${res.pricePerUnit})
                            • Gross Trade Amount: ${formatInr(res.grossTradeAmount)}
                            • Gross Capital Gain: ${formatInr(res.grossCapitalGain)}
                            • LTCG Equity: ${formatInr(res.ltcgEquity)} | STCG Equity: ${formatInr(res.stcgEquity)}
                            • Sec 112A Exemption Applied: ${formatInr(res.sec112aExemptionApplied)}
                            • Projected Tax Liability: ${formatInr(res.estimatedTaxLiability)}
                            • Post-Trade Valuation: ${formatInr(res.postTradeNetWorth)}
                            • Post-Trade Portfolio XIRR: ${String.format("%.2f", res.postTradeXirr)}%
                        """.trimIndent()
                    } catch (e: Exception) {
                        resultText = "⚠️ Simulation RPC failed: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0FF00)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text("Run What-If Simulation", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (resultText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = resultText!!,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
