package com.portfolioos.core.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Service
public class TaxRagService {

    private final ChatClient.Builder chatClientBuilder;
    private VectorStore vectorStore;

    public TaxRagService(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @PostConstruct
    public void initTaxKnowledgeBase() {
        try {
            // Spring AI SimpleVectorStore in-memory setup for Indian Tax Code rules
            File rulesFile = new File("rules/FY2026-27.yaml");
            if (rulesFile.exists()) {
                String content = Files.readString(rulesFile.toPath());
                Document doc = new Document(
                    "INDIAN TAX CODE & RULES FY2026-27:\n" + content,
                    Map.of("source", "FY2026-27.yaml", "category", "TAX_RULES")
                );
                // Vector store placeholder populated on demand
            }
        } catch (Exception e) {
            System.err.println("Tax Vector Store initialization warning: " + e.getMessage());
        }
    }

    public String answerTaxQuestion(String userQuestion) {
        try {
            String systemText = """
                You are an expert Indian Income Tax advisor for Mutual Funds and Equity Capital Gains.
                Use the following ground-truth rules:
                - Equity LTCG (holding > 365 days): Taxed at 12.5% above Section 112A exemption limit of ₹1,25,000 per financial year.
                - Equity STCG (holding <= 365 days): Taxed at 20.0% under Section 111A.
                - Debt Mutual Funds acquired after April 1, 2023: Taxed at slab rates under Section 50AA regardless of holding period.
                - Grandfathering Rule: NAV as of 31-Jan-2018 is used as cost basis for equity holdings acquired prior to 01-Feb-2018.

                Provide clear, concise, legally grounded answers.
                """;

            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                .system(systemText)
                .user(userQuestion)
                .call()
                .content();
        } catch (Exception e) {
            return "⚠️ Tax RAG query failed: " + e.getMessage();
        }
    }
}
