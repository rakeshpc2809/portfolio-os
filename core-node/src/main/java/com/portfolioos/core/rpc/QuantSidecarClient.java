package com.portfolioos.core.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.persistence.DuckDbProjector.NavHistorySeriesEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class QuantSidecarClient {

    private static final Logger log = LoggerFactory.getLogger(QuantSidecarClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public QuantSidecarClient() {
        this(resolveDefaultHost(), 8000);
    }

    public QuantSidecarClient(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    public QuantSidecarClient(String fullUrl) {
        if (fullUrl != null && !fullUrl.isBlank()) {
            this.baseUrl = fullUrl.replace("grpc+tcp://", "http://").replace("tcp://", "http://");
        } else {
            this.baseUrl = "http://" + resolveDefaultHost() + ":8000";
        }
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    private static String resolveDefaultHost() {
        String quantHost = System.getenv("QUANT_SIDECAR_HOST");
        if (quantHost != null && !quantHost.isBlank()) {
            return quantHost;
        }
        String sidecarHost = System.getenv("SIDECAR_HOST");
        if (sidecarHost != null && !sidecarHost.isBlank()) {
            return sidecarHost;
        }
        return "127.0.0.1";
    }

    private static String resolveAuthToken() {
        String token = System.getenv("API_AUTH_TOKEN");
        if (token == null || token.isBlank()) {
            token = System.getProperty("API_AUTH_TOKEN");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("SECURITY CRITICAL: API_AUTH_TOKEN environment variable or system property is required for QuantSidecarClient.");
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runMonteCarloFireSimulation(
        List<Double> dailyReturns,
        double currentCorpus,
        double annualExpense,
        double monthlyContribution,
        int yearsToRetirement,
        int numSimulations
    ) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("daily_returns", dailyReturns != null ? dailyReturns : Collections.emptyList());
            payload.put("current_corpus", currentCorpus);
            payload.put("annual_expense", annualExpense);
            payload.put("monthly_contribution", monthlyContribution);
            payload.put("years_to_retirement", yearsToRetirement);
            payload.put("num_simulations", numSimulations);

            String json = mapper.writeValueAsString(payload);
            String token = resolveAuthToken();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/simulate_fire"))
                .header("Content-Type", "application/json")
                .header("X-Api-Auth-Token", token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), Map.class);
            } else {
                log.warn("Monte Carlo simulation returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.info("Quant sidecar Monte Carlo simulation unavailable ({}). Gracefully continuing with fallback.", e.getMessage());
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> computeBenchmarkAnalytics(
        List<Double> portfolioReturns,
        List<Double> benchmarkReturns,
        String benchmarkName
    ) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("portfolio_returns", portfolioReturns != null ? portfolioReturns : Collections.emptyList());
            payload.put("benchmark_returns", benchmarkReturns != null ? benchmarkReturns : Collections.emptyList());
            payload.put("benchmark_name", benchmarkName != null ? benchmarkName : "NIFTY_50_TRI");

            String json = mapper.writeValueAsString(payload);
            String token = resolveAuthToken();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/analytics/benchmark"))
                .header("Content-Type", "application/json")
                .header("X-Api-Auth-Token", token)
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), Map.class);
            }
        } catch (Exception e) {
            log.info("Quant sidecar benchmark analytics unavailable ({}).", e.getMessage());
        }
        return Collections.emptyMap();
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

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> computeQuantMetricsWithDates(Map<String, NavHistorySeriesEntry> fundNavSeries) {
        if (fundNavSeries == null || fundNavSeries.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<Map<String, Object>> fundsList = new ArrayList<>();
            for (Map.Entry<String, NavHistorySeriesEntry> entry : fundNavSeries.entrySet()) {
                String amfiCode = entry.getKey();
                NavHistorySeriesEntry series = entry.getValue();
                if (series.navs() == null || series.navs().isEmpty()) continue;

                Map<String, Object> fundObj = new HashMap<>();
                fundObj.put("amfi_code", amfiCode);
                fundObj.put("nav_values", series.navs());
                if (series.dates() != null && !series.dates().isEmpty()) {
                    fundObj.put("nav_dates", series.dates());
                }
                fundsList.add(fundObj);
            }

            if (fundsList.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, Object> reqPayload = Map.of("funds", fundsList);
            String json = mapper.writeValueAsString(reqPayload);
            String token = resolveAuthToken();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/analytics/fund_metrics"))
                .header("Content-Type", "application/json")
                .header("X-Api-Auth-Token", token)
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), Map.class);
            }
        } catch (Exception e) {
            log.info("Quant sidecar batch metrics unavailable ({}).", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
