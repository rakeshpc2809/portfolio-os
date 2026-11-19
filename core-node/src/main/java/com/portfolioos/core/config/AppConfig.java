package com.portfolioos.core.config;

import com.portfolioos.core.persistence.MarketDataRepository;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.service.RebalancePlanEngine;
import com.portfolioos.core.service.RebalanceTriggerEvaluator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class AppConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource sqliteDataSource(
        @Value("${sqlite.path:data/tax_ledger.db}") String dbPath
    ) {
        String resolvedPath = DbPathResolver.resolveDatabasePath(dbPath, "SQLITE_PATH");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        String jdbcUrl;
        if (":memory:".equals(resolvedPath)) {
            jdbcUrl = "jdbc:sqlite::memory:";
        } else if (resolvedPath != null && resolvedPath.startsWith("jdbc:sqlite:")) {
            jdbcUrl = resolvedPath;
        } else {
            File file = new File(resolvedPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setIdleTimeout(30000);
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=5000;");
        config.setPoolName("SqliteUnifiedPool");

        return new HikariDataSource(config);
    }

    @Bean
    public EventStorePort eventStore(HikariDataSource sqliteDataSource) {
        return new SqliteEventStore(sqliteDataSource);
    }

    @Bean
    public TriggerHistoryRepository triggerHistoryRepository(HikariDataSource sqliteDataSource) {
        return new TriggerHistoryRepository(sqliteDataSource);
    }

    @Bean
    public MarketDataRepository marketDataRepository(HikariDataSource sqliteDataSource) {
        return new MarketDataRepository(sqliteDataSource);
    }

    @Bean
    public RebalanceTriggerEvaluator rebalanceTriggerEvaluator(TriggerHistoryRepository repository) {
        RebalanceTriggerEvaluator evaluator = new RebalanceTriggerEvaluator(repository);
        RebalancePlanEngine.setTriggerEvaluator(evaluator);
        return evaluator;
    }
}
