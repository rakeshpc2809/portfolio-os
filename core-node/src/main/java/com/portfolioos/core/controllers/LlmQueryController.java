package com.portfolioos.core.controllers;

import com.portfolioos.core.llm.SqlGeneratorService;
import com.portfolioos.core.llm.TaxRagService;
import com.portfolioos.core.service.SimulationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmQueryController {

    private final SqlGeneratorService sqlService;
    private final TaxRagService taxRagService;
    private final SimulationService simulationService;
    private final ChatClient.Builder chatClientBuilder;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public LlmQueryController(
        SqlGeneratorService sqlService,
        TaxRagService taxRagService,
        SimulationService simulationService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.sqlService = sqlService;
        this.taxRagService = taxRagService;
        this.simulationService = simulationService;
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
                // Example tool parameter extraction for paired or single trade
                SimulationService.TradeSimulationRequest simReq = new SimulationService.TradeSimulationRequest(
                    "INF200K01229",
                    "Parag Parikh Flexi Cap Fund",
                    100.0,
                    165.0,
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
    public SseEmitter streamQuery(@RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(60000L);
        executorService.execute(() -> {
            try {
                LlmQueryResponse res = handleQuery(new LlmQueryRequest(prompt));
                String content = res.textResponse();

                if (res.generatedSql() != null && !res.generatedSql().isBlank()) {
                    content += "\n\n```sql\n" + res.generatedSql() + "\n```";
                }

                // Stream tokens word-by-word for live SSE typing effect
                String[] words = content.split(" ");
                for (String word : words) {
                    emitter.send(word + " ");
                    Thread.sleep(30);
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("⚠️ Streaming error: " + e.getMessage());
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        });
        return emitter;
    }
}
