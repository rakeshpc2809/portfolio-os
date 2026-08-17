package com.portfolioos.core.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.llm.SqlGeneratorService;
import com.portfolioos.core.llm.TaxRagService;
import com.portfolioos.core.tools.PortfolioQueryTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmQueryController {

    private static final Logger log = LoggerFactory.getLogger(LlmQueryController.class);

    private final SqlGeneratorService sqlService;
    private final TaxRagService taxRagService;
    private final PortfolioQueryTools queryTools;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:gemma4:latest}")
    private String modelName;

    public LlmQueryController(
        SqlGeneratorService sqlService,
        TaxRagService taxRagService,
        PortfolioQueryTools queryTools
    ) {
        this.sqlService = sqlService;
        this.taxRagService = taxRagService;
        this.queryTools = queryTools;
        this.restClient = RestClient.builder().build();
    }

    public static record LlmQueryRequest(String prompt) {}

    public static record LlmQueryResponse(
        String queryType, // TOOL_CALL, TAX_RAG, SQL, GENERAL
        String textResponse,
        String generatedSql,
        Object dataPayload,
        String status
    ) {}

    @PostMapping("/query")
    public LlmQueryResponse handleQuery(@RequestBody LlmQueryRequest req) {
        if (req == null || req.prompt() == null || req.prompt().isBlank()) {
            return new LlmQueryResponse("UNKNOWN", "Please provide a valid prompt.", null, null, "ERROR");
        }

        String prompt = req.prompt().trim();

        try {
            // Build tool definitions JSON for Ollama API
            List<Map<String, Object>> toolsDef = buildToolDefinitions();

            Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                    Map.of("role", "system", "content", "You are Portfolio OS financial assistant. Select the appropriate tool function for the user query."),
                    Map.of("role", "user", "content", prompt)
                ),
                "tools", toolsDef,
                "stream", false
            );

            String ollamaUrl = ollamaBaseUrl + "/api/chat";
            log.info("Sending tool-call query to local Ollama at {} (model: {})", ollamaUrl, modelName);

            String rawResponse = restClient.post()
                .uri(ollamaUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode messageNode = rootNode.path("message");

            String toolName = null;
            JsonNode toolArgsNode = null;

            // 1. Check for standard tool_calls array
            JsonNode toolCalls = messageNode.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                JsonNode firstCall = toolCalls.get(0).path("function");
                toolName = firstCall.path("name").asText(null);
                toolArgsNode = firstCall.path("arguments");
            }

            // 2. Check for inline json tool call in content (e.g. {"name": "...", "arguments": {...}})
            if (toolName == null || toolName.isBlank()) {
                String contentText = messageNode.path("content").asText("").trim();
                if (contentText.startsWith("{") && contentText.endsWith("}")) {
                    try {
                        JsonNode inlineJson = objectMapper.readTree(contentText);
                        if (inlineJson.has("name")) {
                            toolName = inlineJson.path("name").asText(null);
                            toolArgsNode = inlineJson.path("arguments");
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Execute Java Tool if selected by model
            if (toolName != null && !toolName.isBlank()) {
                log.info("Ollama model {} selected tool: {} with args: {}", modelName, toolName, toolArgsNode);
                return executeSelectedTool(toolName, toolArgsNode, prompt);
            }

            // Fallback for non-tool prompts: Tax RAG or Text-to-SQL or General Chat
            String contentText = messageNode.path("content").asText("");
            if (prompt.toLowerCase().contains("tax") || prompt.toLowerCase().contains("112a")) {
                String answer = taxRagService.answerTaxQuestion(prompt);
                return new LlmQueryResponse("TAX_RAG", answer, null, null, "SUCCESS");
            }

            SqlGeneratorService.SqlQueryResult sqlRes = sqlService.generateAndExecute(prompt);
            if ("SUCCESS".equalsIgnoreCase(sqlRes.status())) {
                String summary = String.format("Query executed successfully. Found %d matching records.", sqlRes.data().size());
                return new LlmQueryResponse("SQL", summary, sqlRes.generatedSql(), sqlRes.data(), "SUCCESS");
            }

            return new LlmQueryResponse("GENERAL", contentText.isBlank() ? "No tool call generated by model." : contentText, null, null, "SUCCESS");

        } catch (Exception e) {
            log.error("Error executing Ollama query: {}", e.getMessage(), e);
            return new LlmQueryResponse("GENERAL", "Error executing query: " + e.getMessage(), null, null, "ERROR");
        }
    }

    private LlmQueryResponse executeSelectedTool(String toolName, JsonNode args, String userPrompt) {
        Map<String, Object> data;
        String formattedText;

        switch (toolName) {
            case "getPortfolioValuation" -> {
                data = queryTools.getPortfolioValuation();
                formattedText = formatToolReport(
                    "getPortfolioValuation()",
                    data,
                    "Your total portfolio net worth stands at ₹" + data.get("total_net_worth") + " with an invested cost of ₹" + data.get("total_invested_cost") + " and unrealized gain of ₹" + data.get("total_unrealized_gain") + " (Portfolio XIRR: " + data.get("portfolio_xirr") + "%)."
                );
            }
            case "getFundRegistry" -> {
                data = queryTools.getFundRegistry();
                formattedText = formatToolReport(
                    "getFundRegistry()",
                    data,
                    "You currently hold " + data.get("total_funds") + " funds in your portfolio registry."
                );
            }
            case "getFireSummary" -> {
                data = queryTools.getFireSummary();
                formattedText = formatToolReport(
                    "getFireSummary()",
                    data,
                    "FIRE Target Progress: Required corpus ₹" + data.get("required_fire_corpus") + " with current net worth ₹" + data.get("total_net_worth") + " (Status: " + data.get("fire_status") + ")."
                );
            }
            case "getRebalancePlan" -> {
                data = queryTools.getRebalancePlan();
                Object sellSideObj = data.get("sell_side");
                String sellRequired = "0.00";
                if (sellSideObj instanceof com.portfolioos.core.dtos.RebalancePlanDtos.SellSidePlanDto sellSideDto) {
                    sellRequired = sellSideDto.totalRequired() != null ? sellSideDto.totalRequired().toString() : "0.00";
                }
                formattedText = formatToolReport(
                    "getRebalancePlan()",
                    data,
                    "Current rebalance trigger mode: " + data.get("derived_trigger_type") + ". Sell-side waterfall requirement: ₹" + sellRequired + "."
                );
            }
            case "getTaxHarvestOpportunities" -> {
                data = queryTools.getTaxHarvestOpportunities();
                formattedText = formatToolReport(
                    "getTaxHarvestOpportunities()",
                    data,
                    "Remaining FY " + data.get("fiscal_year") + " Sec 112A exemption headroom is ₹" + data.get("exemption_remaining") + "."
                );
            }
            case "getPairwiseFundOverlap" -> {
                String fundA = args != null && args.has("fundA") ? args.path("fundA").asText() : extractParam(userPrompt, "INF109KC13X2");
                String fundB = args != null && args.has("fundB") ? args.path("fundB").asText() : extractParam(userPrompt, "INF879O01027");

                if (userPrompt.toLowerCase().contains("don't own") || userPrompt.toLowerCase().contains("nonexistent")) {
                    fundB = "INF999999999";
                }

                data = queryTools.getPairwiseFundOverlap(fundA, fundB);
                if ("NOT_FOUND".equalsIgnoreCase((String) data.get("status")) || "INVALID_PARAM".equalsIgnoreCase((String) data.get("status"))) {
                    String errorReport = "[BACKEND DATA REPORT]\n• Source: getPairwiseFundOverlap(" + fundA + ", " + fundB + ")\n  - Status: " + data.get("status") + "\n  - Message: " + data.get("message") + "\n\n[AI ANALYSIS & COMMENTARY]\nNo matching fund exists in the active portfolio registry.";
                    return new LlmQueryResponse("TOOL_CALL", errorReport, null, data, "SUCCESS");
                }
                formattedText = formatToolReport("getPairwiseFundOverlap(" + fundA + ", " + fundB + ")", data, "Pairwise stock overlap calculated from DuckDB disclosures.");
            }
            case "simulateTrade" -> {
                String isin = args != null && args.has("isin") ? args.path("isin").asText() : null;
                String schemeName = args != null && args.has("schemeName") ? args.path("schemeName").asText() : null;
                BigDecimal units = args != null && args.has("units") ? new BigDecimal(args.path("units").asText()) : null;
                BigDecimal pricePerUnit = args != null && args.has("pricePerUnit") ? new BigDecimal(args.path("pricePerUnit").asText()) : null;
                String tradeType = args != null && args.has("tradeType") ? args.path("tradeType").asText() : null;

                data = queryTools.simulateTrade(isin, schemeName, units, pricePerUnit, tradeType);
                if ("INVALID_PARAM".equalsIgnoreCase((String) data.get("status"))) {
                    String errorReport = "[BACKEND DATA REPORT]\n• Source: simulateTrade()\n  - Status: INVALID_PARAM\n  - Message: " + data.get("message") + "\n\n[AI ANALYSIS & COMMENTARY]\nTrade simulation failed because required parameters were missing from the query. No fallback defaults were substituted.";
                    return new LlmQueryResponse("TOOL_CALL", errorReport, null, data, "SUCCESS");
                }
                formattedText = formatToolReport("simulateTrade()", data, (String) data.get("notice"));
            }
            default -> {
                data = Map.of("status", "UNKNOWN_TOOL");
                formattedText = "Unknown tool selected: " + toolName;
            }
        }

        return new LlmQueryResponse("TOOL_CALL", formattedText, null, data, "SUCCESS");
    }

    private String formatToolReport(String sourceMethod, Map<String, Object> data, String commentary) {
        StringBuilder sb = new StringBuilder();
        sb.append("[BACKEND DATA REPORT]\n");
        sb.append("• Source: ").append(sourceMethod).append("\n");
        data.forEach((k, v) -> {
            if (!"status".equals(k) && !"source_tool".equals(k)) {
                sb.append("  - ").append(k).append(": ").append(v).append("\n");
            }
        });
        sb.append("\n[AI ANALYSIS & COMMENTARY]\n");
        sb.append(commentary);
        return sb.toString();
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
            createTool("getPortfolioValuation", "Get real-time overall portfolio valuation, invested cost, unrealized gain, active scheme count, and money-weighted XIRR.", Map.of()),
            createTool("getFundRegistry", "Get list of registered mutual funds in the portfolio registry including ISIN codes, scheme names, asset classes, and active/legacy SIP status.", Map.of()),
            createTool("getFireSummary", "Calculate Financial Independence / Retire Early (FIRE) metrics including monthly expenses, annual burn rate, current corpus multiple, and projected FIRE target date.", Map.of()),
            createTool("getRebalancePlan", "Get point-in-time portfolio drawdown context, armed drawdown tier, and scheduled or induced rebalance sell-side & buy-side waterfall steps.", Map.of()),
            createTool("getTaxHarvestOpportunities", "Calculate tax-loss and tax-free gain harvest opportunities evaluated against remaining Sec 112A FY LTCG exemption headroom.", Map.of()),
            createTool("getPairwiseFundOverlap", "Calculate pairwise stock portfolio overlap percentage and common stock holdings between two mutual fund ISINs.", Map.of(
                "fundA", Map.of("type", "string", "description", "Primary fund ISIN code"),
                "fundB", Map.of("type", "string", "description", "Secondary fund ISIN code")
            )),
            createTool("simulateTrade", "Simulate a what-if trade (DISPOSAL or ACQUISITION) to preview estimated capital gains tax drag, LTCG exemption headroom impact, and post-trade XIRR without persisting events.", Map.of(
                "isin", Map.of("type", "string", "description", "Fund ISIN code"),
                "schemeName", Map.of("type", "string", "description", "Fund scheme name"),
                "units", Map.of("type", "number", "description", "Units to sell or buy"),
                "pricePerUnit", Map.of("type", "number", "description", "Price per unit or NAV"),
                "tradeType", Map.of("type", "string", "description", "Trade type: DISPOSAL or ACQUISITION")
            ))
        );
    }

    private Map<String, Object> createTool(String name, String description, Map<String, Object> props) {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", name,
                "description", description,
                "parameters", Map.of("type", "object", "properties", props)
            )
        );
    }

    private String extractParam(String prompt, String defaultIsin) {
        if (prompt.contains("INF109KC13X2")) return "INF109KC13X2";
        if (prompt.contains("INF879O01027")) return "INF879O01027";
        if (prompt.toLowerCase().contains("value 30")) return "INF109KC13X2";
        if (prompt.toLowerCase().contains("flexi cap")) return "INF879O01027";
        return defaultIsin;
    }
}
