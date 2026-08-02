package com.portfolioos.core.rpc;

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
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (fundNavSeries == null || fundNavSeries.isEmpty()) {
            return out;
        }

        try {
            Location location = Location.forGrpcInsecure(host, port);
            try (FlightClient client = FlightClient.builder(allocator, location).build()) {

                Schema inSchema = new Schema(List.of(
                    new Field("amfi_code", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_value", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null)
                ));

                try (VectorSchemaRoot inRoot = VectorSchemaRoot.create(inSchema, allocator)) {
                    int totalRows = fundNavSeries.values().stream().mapToInt(List::size).sum();
                    VarCharVector codeVec = (VarCharVector) inRoot.getVector("amfi_code");
                    Float8Vector navVec = (Float8Vector) inRoot.getVector("nav_value");
                    codeVec.allocateNew(totalRows);
                    navVec.allocateNew(totalRows);

                    int row = 0;
                    for (Map.Entry<String, List<Double>> entry : fundNavSeries.entrySet()) {
                        byte[] codeBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                        for (double nav : entry.getValue()) {
                            codeVec.setSafe(row, codeBytes);
                            navVec.setSafe(row, nav);
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
}
