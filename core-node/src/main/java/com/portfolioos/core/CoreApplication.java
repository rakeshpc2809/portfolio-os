package com.portfolioos.core;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupRunner(EventStorePort eventStore, DuckDbProjector duckDbProjector) {
        return args -> {
            System.out.println("Initializing DuckDB Projection from SQLite ledger...");
            try {
                duckDbProjector.projectEvents(eventStore.getAllEvents());
                System.out.println("DuckDB projection loaded successfully.");
            } catch (Exception e) {
                System.err.println("Failed to build startup projection: " + e.getMessage());
            }
        };
    }
}
