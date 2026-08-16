package com.portfolioos.core.controllers;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.service.LedgerCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementsController {

    private final EventStorePort eventStore;
    private final DuckDbProjector duckDbProjector;
    private final LedgerCacheService cacheService;
    private final RestClient restClient;
    private final String authToken;
    private final String sidecarUrl;

    public StatementsController(
        EventStorePort eventStore,
        DuckDbProjector duckDbProjector,
        LedgerCacheService cacheService,
        @Value("${quant-sidecar.url:http://quant-sidecar:8000}") String sidecarUrl,
        @Value("${api.auth.token:dev_secret_key_123}") String authToken
    ) {
        this.eventStore = eventStore;
        this.duckDbProjector = duckDbProjector;
        this.cacheService = cacheService;
        this.authToken = authToken;
        this.sidecarUrl = sidecarUrl;
        this.restClient = RestClient.builder().baseUrl(sidecarUrl).build();
    }

    public record ParsedEventDto(
        String id,
        String assetId,
        String assetName,
        String isin,
        String eventType,
        String eventDate,
        BigDecimal units,
        BigDecimal pricePerUnit,
        BigDecimal grossAmount,
        String sourceDocumentId
    ) {}

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadStatement(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "password", required = false) String password
    ) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            
            body.add("file", fileResource);
            if (password != null && !password.isEmpty()) {
                body.add("password", password);
            }

            // POST to parser sidecar with authentication header & candidate host fallbacks
            String[] candidateUrls = new String[] {
                sidecarUrl,
                "http://127.0.0.1:8000",
                "http://host.containers.internal:8000",
                "http://172.17.0.1:8000",
                "http://localhost:8000"
            };

            ResponseEntity<ParsedEventDto[]> response = null;
            Exception lastException = null;

            for (String targetUrl : candidateUrls) {
                if (targetUrl == null || targetUrl.isBlank()) continue;
                try {
                    RestClient candidateClient = RestClient.builder().baseUrl(targetUrl).build();
                    response = candidateClient.post()
                        .uri("/api/v1/parse")
                        .header("X-Api-Auth-Token", authToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .toEntity(ParsedEventDto[].class);
                    if (response != null && response.getStatusCode().is2xxSuccessful()) {
                        System.out.println("Successfully connected to CAS parser sidecar at: " + targetUrl);
                        break;
                    }
                } catch (Exception ex) {
                    System.err.println("Sidecar parsing attempt failed for candidate [" + targetUrl + "]: " + ex.getMessage());
                    lastException = ex;
                }
            }

            if (response == null || response.getBody() == null) {
                throw new RuntimeException("All parser sidecar host candidates failed: " + (lastException != null ? lastException.getMessage() : "No response"));
            }

            ParsedEventDto[] dtoList = response.getBody();
            if (dtoList == null || dtoList.length == 0) {
                return ResponseEntity.ok(List.of());
            }

            // Convert to domain entities and append to event store
            List<TaxEvent> taxEvents = new java.util.ArrayList<>();
            for (ParsedEventDto dto : dtoList) {
                TaxEvent te = new TaxEvent(
                    dto.id() != null ? dto.id() : UUID.randomUUID().toString(),
                    dto.assetId(),
                    dto.assetName(),
                    dto.isin(),
                    EventType.valueOf(dto.eventType()),
                    LocalDate.parse(dto.eventDate()),
                    dto.units(),
                    dto.pricePerUnit(),
                    dto.grossAmount(),
                    dto.sourceDocumentId(),
                    Instant.now()
                );
                taxEvents.add(te);
            }

            // Write to SQLite
            eventStore.appendEvents(taxEvents);

            // Re-project events in DuckDB
            List<TaxEvent> allEvents = eventStore.getAllEvents();
            duckDbProjector.projectEvents(allEvents);

            // Immediately invalidate central cache so UI updates in real-time
            cacheService.invalidateCache();

            return ResponseEntity.ok(dtoList);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("File reading failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload and parsing failed: " + e.getMessage());
        }
    }
}
