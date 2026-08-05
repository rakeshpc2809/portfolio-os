package com.portfolioos.core.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaxRagService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;

    public TaxRagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void initTaxKnowledgeBase() {
        try {
            File rulesFile = new File("rules/FY2026-27.yaml");
            if (rulesFile.exists()) {
                String content = Files.readString(rulesFile.toPath());
                String[] sections = content.split("\n\n");
                List<Document> docs = new ArrayList<>();
                for (int i = 0; i < sections.length; i++) {
                    if (!sections[i].isBlank()) {
                        docs.add(new Document(
                            sections[i].trim(),
                            Map.of("source", "FY2026-27.yaml", "section_id", i)
                        ));
                    }
                }
                if (!docs.isEmpty()) {
                    vectorStore.add(docs);
                }
            }
        } catch (Exception e) {
            System.err.println("Tax Vector Store initialization warning: " + e.getMessage());
        }
    }

    public String answerTaxQuestion(String userQuestion) {
        try {
            List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.query(userQuestion).withTopK(3)
            );

            String retrievedContext = similarDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n---\n"));

            if (retrievedContext.isBlank()) {
                retrievedContext = """
                    - Equity LTCG (holding > 365 days): Taxed at 12.5% above Section 112A exemption limit of ₹1,25,000 per financial year.
                    - Equity STCG (holding <= 365 days): Taxed at 20.0% under Section 111A.
                    - Debt Mutual Funds acquired after April 1, 2023: Taxed at slab rates under Section 50AA regardless of holding period.
                    - Grandfathering Rule: NAV as of 31-Jan-2018 is used as cost basis for equity holdings acquired prior to 01-Feb-2018.
                    """;
            }

            String systemPrompt = """
                You are an expert Indian Income Tax advisor for Mutual Funds and Equity Capital Gains.
                Use the following retrieved ground-truth tax rules to answer the user's question:

                %s

                Provide clear, concise, legally grounded answers.
                """.formatted(retrievedContext);

            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                .system(systemPrompt)
                .user(userQuestion)
                .call()
                .content();
        } catch (Exception e) {
            return "⚠️ Tax RAG query failed: " + e.getMessage();
        }
    }
}
