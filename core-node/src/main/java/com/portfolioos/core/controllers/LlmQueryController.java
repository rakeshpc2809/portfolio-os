package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.llm.SqlGeneratorService;
import com.portfolioos.core.llm.TaxRagService;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.service.SimulationService;
import com.portfolioos.core.service.TaxOptimizationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmQueryController {

    private final SqlGeneratorService sqlService;
    private final TaxRagService taxRagService;
    private final SimulationService simulationService;
    private final PortfolioValuationService valuationService;
    private final TaxOptimizationService taxService;
    private final ChatClient.Builder chatClientBuilder;

    public LlmQueryController(
        SqlGeneratorService sqlService,
        TaxRagService taxRagService,
        SimulationService simulationService,
        PortfolioValuationService valuationService,
        TaxOptimizationService taxService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.sqlService = sqlService;
        this.taxRagService = taxRagService;
        this.simulationService = simulationService;
        this.valuationService = valuationService;
        this.taxService = taxService;
        this.chatClientBuilder = chatClientBuilder;
    }

    public static record LlmQueryRequest(String prompt) {}

    public static record LlmQueryResponse(
        String queryType, // SQL, TOOL_SIMULATION, TAX_RAG, GENERAL
        String textResponse,
        String generatedSql,
        Object dataPayload,
        String status
    ) {}

    private String buildPortfolioSystemPrompt() {
        try {
            PortfolioSummaryResponse summary = valuationService.getPortfolioSummary("2026-27");
            List<HoldingDetailDto> holdings = valuationService.getHoldings();
            com.portfolioos.core.reporting.ExemptionTracker.ExemptionStatus exemption = taxService.getExemptionStatus("2026-27");

            StringBuilder sb = new StringBuilder();
            sb.append("You are an expert AI Portfolio & Tax Advisor for Portfolio OS.\n");
            sb.append("Below is the user's REAL, VERIFIED live portfolio snapshot as of FY 2026-27:\n\n");
            sb.append("--- PORTFOLIO SUMMARY ---\n");
            sb.append("• Total Net Worth: ₹").append(summary.totalCurrentValue()).append("\n");
            sb.append("• Total Invested Cost: ₹").append(summary.totalInvested()).append("\n");
            sb.append("• Total Unrealized Gain: ₹").append(summary.totalUnrealizedGain()).append("\n");
            sb.append("• Money-Weighted XIRR: ").append(summary.xirrPercentage()).append("\n");
            sb.append("• Active Scheme Count: ").append(summary.activeHoldingCount()).append("\n\n");

            sb.append("--- OPEN HOLDINGS & SCHEME ALLOCATION ---\n");
            for (HoldingDetailDto h : holdings) {
                sb.append(String.format("• %s (%s): Invested ₹%s | Value ₹%s | Gain ₹%s (%s%%) | Alloc %s%%\n",
                    h.assetName(), h.category(), h.investedValue(), h.currentValue(), h.unrealizedGain(), h.unrealizedGainPct(), h.allocationPct()));
            }
            sb.append("\n");

            sb.append("--- TAX & EXEMPTION HEADROOM ---\n");
            sb.append("• FY 2026-27 Sec 112A LTCG Exemption Headroom Remaining: ₹").append(exemption.exemptionRemaining()).append("\n");
            sb.append("• Realized Taxable LTCG in FY 2026-27: ₹").append(exemption.taxableLtcg()).append("\n\n");

            sb.append("CRITICAL: Answer user queries strictly using these exact verified numbers. Be concise, mathematically accurate, and professional.");
            return sb.toString();
        } catch (Exception e) {
            return "You are an AI assistant for Portfolio OS. Answer portfolio questions concisely.";
        }
    }

    @PostMapping("/query")
    public LlmQueryResponse handleQuery(@RequestBody LlmQueryRequest req) {
        if (req == null || req.prompt() == null || req.prompt().isBlank()) {
            return new LlmQueryResponse("UNKNOWN", "Please provide a valid prompt.", null, null, "ERROR");
        }

        String prompt = req.prompt().trim();
        String promptLower = prompt.toLowerCase();

        // 1. Tool Call Interception for Trade Simulation
        if (promptLower.contains("simulate") || promptLower.contains("what-if") || promptLower.contains("what if")) {
            try {
                SimulationService.TradeSimulationRequest simReq = new SimulationService.TradeSimulationRequest(
                    "INF200K01229",
                    "Parag Parikh Flexi Cap Fund",
                    new java.math.BigDecimal("100.0"),
                    new java.math.BigDecimal("165.0"),
                    null,
                    promptLower.contains("sell") ? "DISPOSAL" : "ACQUISITION"
                );
                SimulationService.TradeSimulationResult res = simulationService.simulateTrade(simReq);
                return new LlmQueryResponse("TOOL_SIMULATION", res.taxSummaryNotice(), null, res, "SUCCESS");
            } catch (Exception e) {
                return new LlmQueryResponse("TOOL_SIMULATION", "Simulation failed: " + e.getMessage(), null, null, "ERROR");
            }
        }

        // 2. Tax RAG Engine for Tax Code Questions
        if (promptLower.contains("tax") || promptLower.contains("112a") || promptLower.contains("50aa") || promptLower.contains("ltcg") || promptLower.contains("stcg")) {
            String answer = taxRagService.answerTaxQuestion(prompt);
            return new LlmQueryResponse("TAX_RAG", answer, null, null, "SUCCESS");
        }

        // 3. DuckDB Text-to-SQL Pipeline
        SqlGeneratorService.SqlQueryResult sqlRes = sqlService.generateAndExecute(prompt);
        if ("SUCCESS".equalsIgnoreCase(sqlRes.status())) {
            String summary = String.format("Query executed successfully. Found %d matching records.", sqlRes.data().size());
            return new LlmQueryResponse("SQL", summary, sqlRes.generatedSql(), sqlRes.data(), "SUCCESS");
        }

        return new LlmQueryResponse("GENERAL", "Could not execute query: " + sqlRes.errorMessage(), null, null, "ERROR");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQuery(@RequestParam("prompt") String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Flux.just("Please provide a valid prompt.");
        }
        try {
            String systemPrompt = buildPortfolioSystemPrompt();
            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .stream()
                .content()
                .onErrorResume(e -> Flux.just("⚠️ Local Ollama LLM Service Error: " + e.getMessage()));
        } catch (Exception e) {
            return Flux.just("⚠️ Streaming error: " + e.getMessage());
        }
    }
}
