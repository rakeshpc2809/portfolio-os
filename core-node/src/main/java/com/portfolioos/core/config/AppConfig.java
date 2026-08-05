package com.portfolioos.core.config;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.rpc.FlightRpcClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public EventStorePort eventStore(
        @Value("${sqlite.path:data/tax_ledger.db}") String dbPath
    ) {
        return new SqliteEventStore(dbPath);
    }

    @Bean
    public DuckDbProjector duckDbProjector(
        @Value("${duckdb.path:data/tax_ledger.duckdb}") String dbPath
    ) {
        return new DuckDbProjector(dbPath);
    }

    @Bean
    public FlightRpcClient flightRpcClient(
        @Value("${quant-sidecar.flight.host:quant-sidecar}") String host,
        @Value("${quant-sidecar.flight.port:8001}") int port
    ) {
        return new FlightRpcClient(host, port);
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(
        @Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaUrl
    ) {
        String resolvedUrl = ollamaUrl;
        if (ollamaUrl.contains("localhost") || ollamaUrl.contains("127.0.0.1")) {
            // Test if running inside container and target host gateway if needed
            resolvedUrl = "http://127.0.0.1:11434";
        }
        OllamaApi ollamaApi = new OllamaApi(resolvedUrl);
        OllamaChatModel chatModel = new OllamaChatModel(
            ollamaApi,
            OllamaOptions.create().withModel("qwen2.5-coder:3b")
        );
        return ChatClient.builder(chatModel);
    }

    @Bean
    public org.springframework.ai.ollama.OllamaEmbeddingModel embeddingModel(
        @Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaUrl
    ) {
        String resolvedUrl = ollamaUrl;
        if (ollamaUrl.contains("localhost") || ollamaUrl.contains("127.0.0.1")) {
            resolvedUrl = "http://127.0.0.1:11434";
        }
        OllamaApi ollamaApi = new OllamaApi(resolvedUrl);
        return new org.springframework.ai.ollama.OllamaEmbeddingModel(
            ollamaApi,
            OllamaOptions.create().withModel("nomic-embed-text")
        );
    }

    @Bean
    public org.springframework.ai.vectorstore.VectorStore vectorStore(
        org.springframework.ai.ollama.OllamaEmbeddingModel embeddingModel
    ) {
        return new org.springframework.ai.vectorstore.SimpleVectorStore(embeddingModel);
    }
}
