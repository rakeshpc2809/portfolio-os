package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.service.StatementIngestionUseCase;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementsController {

    private final StatementIngestionUseCase ingestionUseCase;
    private final RestClient restClient;
    private final String authToken;
    private final String sidecarUrl;

    public StatementsController(
        StatementIngestionUseCase ingestionUseCase,
        @Value("${quant-sidecar.url:http://quant-sidecar:8000}") String sidecarUrl,
        @Value("${api.auth.token:dev_secret_key_123}") String authToken
    ) {
        this.ingestionUseCase = ingestionUseCase;
        this.authToken = authToken;
        this.sidecarUrl = sidecarUrl;
        this.restClient = RestClient.builder().baseUrl(sidecarUrl).build();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadStatement(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "password", required = false, defaultValue = "") String password
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Uploaded statement file is empty.");
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "statement.pdf";
                }
            });
            body.add("password", password);

            String[] candidates = new String[]{
                this.sidecarUrl,
                "http://localhost:8000",
                "http://127.0.0.1:8000"
            };

            ResponseEntity<ParsedEventDto[]> response = null;
            Exception lastException = null;

            for (String targetUrl : candidates) {
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
                        break;
                    }
                } catch (Exception ex) {
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

            ingestionUseCase.ingestParsedEvents(dtoList);

            return ResponseEntity.ok(dtoList);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("File reading failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload and parsing failed: " + e.getMessage());
        }
    }
}
