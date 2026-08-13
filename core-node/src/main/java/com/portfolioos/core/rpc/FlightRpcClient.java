package com.portfolioos.core.rpc;

import com.portfolioos.core.persistence.DuckDbProjector.NavHistorySeriesEntry;
import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FlightRpcClient {

    private final String host;
    private final int port;
    private final String flightUrl;
    private final BufferAllocator allocator;

    public FlightRpcClient() {
        this(
            System.getenv("QUANT_SIDECAR_HOST") != null && !System.getenv("QUANT_SIDECAR_HOST").isEmpty()
                ? System.getenv("QUANT_SIDECAR_HOST")
                : "127.0.0.1",
            8001
        );
    }

    public FlightRpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.flightUrl = "grpc+tcp://" + host + ":" + port;
        this.allocator = new RootAllocator(Long.MAX_VALUE);
    }

    public FlightRpcClient(String flightUrl) {
        this.flightUrl = flightUrl;
        URI uri = URI.create(flightUrl.replace("grpc+tcp://", "http://"));
        this.host = uri.getHost() != null ? uri.getHost() : "quant-sidecar";
        this.port = uri.getPort() > 0 ? uri.getPort() : 8001;
        this.allocator = new RootAllocator(Long.MAX_VALUE);
    }

    public Map<String, Map<String, Object>> computeQuantMetrics(Map<String, List<Double>> fundNavSeries) {
        Map<String, NavHistorySeriesEntry> adapterMap = new HashMap<>();
        if (fundNavSeries != null) {
            for (Map.Entry<String, List<Double>> entry : fundNavSeries.entrySet()) {
                adapterMap.put(entry.getKey(), new NavHistorySeriesEntry(entry.getValue(), Collections.emptyList()));
            }
        }
        return computeQuantMetricsWithDates(adapterMap);
    }

    public Map<String, Map<String, Object>> computeQuantMetricsWithDates(Map<String, NavHistorySeriesEntry> fundNavSeries) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (fundNavSeries == null || fundNavSeries.isEmpty()) {
            return out;
        }

        int totalRows = fundNavSeries.values().stream().mapToInt(e -> e.navs().size()).sum();
        if (totalRows == 0) {
            return out;
        }

        try {
            Location location = Location.forGrpcInsecure(host, port);
            try (FlightClient client = FlightClient.builder(allocator, location).build()) {

                Schema inSchema = new Schema(List.of(
                    new Field("amfi_code", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_date", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_value", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null)
                ));

                try (VectorSchemaRoot inRoot = VectorSchemaRoot.create(inSchema, allocator)) {
                    VarCharVector codeVec = (VarCharVector) inRoot.getVector("amfi_code");
                    VarCharVector dateVec = (VarCharVector) inRoot.getVector("nav_date");
                    Float8Vector navVec = (Float8Vector) inRoot.getVector("nav_value");
                    codeVec.allocateNew(totalRows * 32L, totalRows);
                    dateVec.allocateNew(totalRows * 16L, totalRows);
                    navVec.allocateNew(totalRows);

                    int row = 0;
                    for (Map.Entry<String, NavHistorySeriesEntry> entry : fundNavSeries.entrySet()) {
                        byte[] codeBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                        List<Double> navs = entry.getValue().navs();
                        List<String> dates = entry.getValue().dates();

                        for (int i = 0; i < navs.size(); i++) {
                            codeVec.setSafe(row, codeBytes);
                            if (i < dates.size() && dates.get(i) != null) {
                                dateVec.setSafe(row, dates.get(i).getBytes(StandardCharsets.UTF_8));
                            } else {
                                dateVec.setSafe(row, "".getBytes(StandardCharsets.UTF_8));
                            }
                            navVec.setSafe(row, navs.get(i));
                            row++;
                        }
                    }
                    inRoot.setRowCount(totalRows);

                    FlightDescriptor descriptor = FlightDescriptor.path("quant_metrics");
                    FlightClient.ExchangeReaderWriter exchange = client.doExchange(descriptor);

                    FlightClient.ClientStreamListener writer = exchange.getWriter();
                    writer.start(inRoot);
                    writer.putNext();
                    writer.completed();

                    try (FlightStream reader = exchange.getReader()) {
                        while (reader.next()) {
                            VectorSchemaRoot outRoot = reader.getRoot();
                            VarCharVector outCode = (VarCharVector) outRoot.getVector("amfi_code");
                            for (int i = 0; i < outRoot.getRowCount(); i++) {
                                String code = new String(outCode.get(i), StandardCharsets.UTF_8);
                                Map<String, Object> metrics = new HashMap<>();
                                for (Field f : outRoot.getSchema().getFields()) {
                                    if (f.getName().equals("amfi_code")) continue;
                                    metrics.put(f.getName(), outRoot.getVector(f.getName()).getObject(i));
                                }
                                out.put(code, metrics);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Arrow Flight quant metrics call error: " + e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runMonteCarloFireSimulation(List<Double> dailyReturns, double currentCorpus, double annualExpense, double monthlyContribution, int yearsToRetirement, int numSimulations) {
        String targetHost = System.getenv("QUANT_SIDECAR_HOST");
        if (targetHost == null || targetHost.isBlank()) {
            targetHost = "127.0.0.1";
        }

        System.out.println("FlightRpcClient: Starting runMonteCarloFireSimulation call. TargetHost=" + targetHost);
        for (String h : List.of(targetHost, "127.0.0.1", "localhost", "quant-sidecar")) {
            try {
                Location location = Location.forGrpcInsecure(h, port);
                try (FlightClient client = FlightClient.builder(allocator, location).build()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("daily_returns", dailyReturns != null ? dailyReturns : Collections.emptyList());
                    payload.put("current_corpus", currentCorpus);
                    payload.put("annual_expense", annualExpense);
                    payload.put("monthly_contribution", monthlyContribution);
                    payload.put("years_to_retirement", yearsToRetirement);
                    payload.put("num_simulations", numSimulations);

                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    byte[] bytes = mapper.writeValueAsBytes(payload);

                    Action action = new Action("fire_simulation", bytes);
                    Iterator<Result> results = client.doAction(action);
                    if (results.hasNext()) {
                        Result res = results.next();
                        return mapper.readValue(res.getBody(), Map.class);
                    }
                }
            } catch (Exception e) {
                System.err.println("Flight RPC attempt for host " + h + " failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.err.println("Flight RPC Monte Carlo FIRE simulation error: all host candidates failed. Triggering HTTP fallback...");
        return runMonteCarloFireSimulationHttpFallback(dailyReturns, currentCorpus, annualExpense, monthlyContribution, yearsToRetirement, numSimulations);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runMonteCarloFireSimulationHttpFallback(List<Double> dailyReturns, double currentCorpus, double annualExpense, double monthlyContribution, int yearsToRetirement, int numSimulations) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("daily_returns", dailyReturns != null ? dailyReturns : Collections.emptyList());
            payload.put("current_corpus", currentCorpus);
            payload.put("annual_expense", annualExpense);
            payload.put("monthly_contribution", monthlyContribution);
            payload.put("years_to_retirement", yearsToRetirement);
            payload.put("num_simulations", numSimulations);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(payload);

            String token = resolveAuthToken();

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://127.0.0.1:8000/api/v1/simulate_fire"))
                .header("Content-Type", "application/json")
                .header("X-Api-Auth-Token", token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("HTTP fallback succeeded for Monte Carlo FIRE simulation.");
                return mapper.readValue(response.body(), Map.class);
            }
        } catch (Exception e) {
            System.err.println("HTTP fallback for Monte Carlo FIRE simulation failed: " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> computeBenchmarkAnalytics(List<Double> portfolioReturns, List<Double> benchmarkReturns, String benchmarkName) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("portfolio_returns", portfolioReturns != null ? portfolioReturns : Collections.emptyList());
            payload.put("benchmark_returns", benchmarkReturns != null ? benchmarkReturns : Collections.emptyList());
            payload.put("benchmark_name", benchmarkName != null ? benchmarkName : "NIFTY_50_TRI");

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(payload);

            String token = resolveAuthToken();

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://127.0.0.1:8000/api/v1/analytics/benchmark"))
                .header("Content-Type", "application/json")
                .header("X-Api-Auth-Token", token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), Map.class);
            }
        } catch (Exception e) {
            System.err.println("Benchmark analytics request failed: " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    private static String resolveAuthToken() {
        String token = System.getenv("API_AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            String activeProfiles = System.getProperty("spring.profiles.active", "");
            if (activeProfiles.contains("test") && System.getProperty("API_AUTH_TOKEN") != null) {
                token = System.getProperty("API_AUTH_TOKEN");
            }
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing required environment variable 'API_AUTH_TOKEN'. FlightRpcClient refuses unauthenticated RPC call.");
        }
        return token;
    }
}
