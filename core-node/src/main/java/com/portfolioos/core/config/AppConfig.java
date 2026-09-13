package com.portfolioos.core.config;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.rpc.FlightRpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public EventStorePort eventStore(
        @Value("${sqlite.path:data/tax_ledger.db}") String dbPath
    ) {
        return new SqliteEventStore(DbPathResolver.resolveDatabasePath(dbPath, "SQLITE_PATH"));
    }

    @Bean
    public DuckDbProjector duckDbProjector(
        @Value("${duckdb.path:data/tax_ledger.duckdb}") String dbPath
    ) {
        return new DuckDbProjector(DbPathResolver.resolveDatabasePath(dbPath, "DUCKDB_PATH"));
    }

    @Bean
    public FlightRpcClient flightRpcClient(
        @Value("${quant-sidecar.flight.host:${QUANT_SIDECAR_HOST:127.0.0.1}}") String host,
        @Value("${quant-sidecar.flight.port:8001}") int port
    ) {
        return new FlightRpcClient(host, port);
    }
}
