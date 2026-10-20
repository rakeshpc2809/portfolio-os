package com.portfolioos.core;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
@EnableScheduling
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupRunner(EventStorePort eventStore, DuckDbProjector duckDbProjector) {
        return args -> {
            System.out.println("Verifying SQLite HMAC ledger integrity...");
            if (!eventStore.verifyLedgerIntegrity()) {
                throw new IllegalStateException("CRITICAL: SQLite HMAC ledger chain integrity check failed! Potential tampering or corruption.");
            }
            System.out.println("Ledger integrity verified successfully. Initializing DuckDB Projection...");
            try {
                duckDbProjector.projectEvents(eventStore.getAllEvents());
                System.out.println("DuckDB projection loaded successfully.");
            } catch (Exception e) {
                System.err.println("Failed to build startup projection: " + e.getMessage());
            }
        };
    }
}
