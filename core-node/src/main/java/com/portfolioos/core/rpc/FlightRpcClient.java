package com.portfolioos.core.rpc;

import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlightRpcClient {

    private final String host;
    private final int port;
    private final String flightUrl;
    private final BufferAllocator allocator;

    public FlightRpcClient() {
        this("quant-sidecar", 8001);
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
        Map<String, Map<String, Object>> results = new HashMap<>();
        if (fundNavSeries.isEmpty()) {
            return results;
        }

        try {
            Location location = Location.forGrpcInsecure(host, port);
            try (FlightClient client = FlightClient.builder(allocator, location).build()) {
                // Connection test handshake
                Iterable<ActionType> actions = client.listActions();
            }
        } catch (Exception e) {
            System.err.println("Arrow Flight connection check: " + e.getMessage());
        }

        return results;
    }
}
