package com.portfolioos.core.controllers;

import com.portfolioos.core.service.StatementIngestionUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatementsControllerTest {

    @Test
    void testUploadStatementEmptyFileReturnsJsonErrorEnvelope() {
        StatementIngestionUseCase ingestionUseCase = Mockito.mock(StatementIngestionUseCase.class);
        StatementsController controller = new StatementsController(
            ingestionUseCase,
            "http://127.0.0.1:8000",
            "test_token"
        );

        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        ResponseEntity<?> response = controller.uploadStatement(emptyFile, "pass");

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("EMPTY_FILE", body.get("error"));
        assertEquals("Uploaded statement file is empty.", body.get("message"));
    }

    @Test
    void testUploadStatementSidecarFailureReturnsJsonErrorEnvelope() {
        StatementIngestionUseCase ingestionUseCase = Mockito.mock(StatementIngestionUseCase.class);
        // Point to an unreachable port to provoke sidecar failure
        StatementsController controller = new StatementsController(
            ingestionUseCase,
            "http://127.0.0.1:59999",
            "test_token"
        );

        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy-content".getBytes());
        ResponseEntity<?> response = controller.uploadStatement(file, "pass");

        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("UPLOAD_FAILED", body.get("error"));
        assertNotNull(body.get("message"));
    }
}
