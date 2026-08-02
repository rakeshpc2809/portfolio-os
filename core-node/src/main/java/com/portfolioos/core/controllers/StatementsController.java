package com.portfolioos.core.controllers;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
    private final RestClient restClient;

    public StatementsController(
        EventStorePort eventStore,
        DuckDbProjector duckDbProjector,
        @Value("${quant-sidecar.url:http://quant-sidecar:8000}") String sidecarUrl
    ) {
        this.eventStore = eventStore;
        this.duckDbProjector = duckDbProjector;
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
            // Forward request to sidecar
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Convert file to ByteArrayResource for multipart formatting
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

            // POST to parser sidecar
            ResponseEntity<ParsedEventDto[]> response = restClient.post()
                .uri("/api/v1/parse")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ParsedEventDto[].class);

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

            return ResponseEntity.ok(dtoList);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("File reading failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload and parsing failed: " + e.getMessage());
        }
    }
}
