package com.portfolioos.core.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.reconciliation.ReconciliationGateFailureException;
import com.portfolioos.core.service.StatementIngestionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementsController {

    private static final Logger log = LoggerFactory.getLogger(StatementsController.class);
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB

    private final StatementIngestionUseCase ingestionUseCase;
    private final String customPythonPath;
    private final String scriptPath;
    private final ObjectMapper objectMapper;

    @Autowired
    public StatementsController(
        StatementIngestionUseCase ingestionUseCase,
        @Value("${cas.parser.python:}") String customPythonPath,
        @Value("${cas.parser.script:quant-sidecar/parse_cas.py}") String scriptPath
    ) {
        this.ingestionUseCase = ingestionUseCase;
        this.customPythonPath = customPythonPath;
        this.scriptPath = scriptPath;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public StatementsController(StatementIngestionUseCase ingestionUseCase) {
        this(ingestionUseCase, "", "quant-sidecar/parse_cas.py");
    }

    private String resolvePythonBinary() {
        if (customPythonPath != null && !customPythonPath.trim().isEmpty()) {
            File f = new File(customPythonPath.trim());
            if (f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        String userDir = System.getProperty("user.dir", ".");
        File venv1 = new File(userDir, "quant-sidecar/.venv/bin/python");
        if (venv1.canExecute()) {
            return venv1.getAbsolutePath();
        }
        File venv2 = new File(userDir, "../quant-sidecar/.venv/bin/python");
        if (venv2.canExecute()) {
            return venv2.getAbsolutePath();
        }
        File venv3 = new File("quant-sidecar/.venv/bin/python");
        if (venv3.canExecute()) {
            return venv3.getAbsolutePath();
        }
        return "python3";
    }

    private String resolveScriptPath() {
        if (scriptPath != null && !scriptPath.trim().isEmpty()) {
            File s0 = new File(scriptPath.trim());
            if (s0.exists()) {
                return s0.getAbsolutePath();
            }
        }
        String target = (scriptPath != null && !scriptPath.trim().isEmpty()) ? scriptPath.trim() : "quant-sidecar/parse_cas.py";
        String userDir = System.getProperty("user.dir", ".");
        File s1 = new File(userDir, target);
        if (s1.exists()) {
            return s1.getAbsolutePath();
        }
        File s2 = new File(userDir, "../" + target);
        if (s2.exists()) {
            return s2.getAbsolutePath();
        }
        return target;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadStatement(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "password", required = false, defaultValue = "") String password
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "EMPTY_FILE",
                "message", "Uploaded statement file is empty."
            ));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(413).body(Map.of(
                "error", "PAYLOAD_TOO_LARGE",
                "message", "Uploaded statement exceeds maximum allowed size of 25MB."
            ));
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.lastIndexOf('.') >= 0) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.equals(".pdf") && !ext.equals(".csv")) {
            return ResponseEntity.status(422).body(Map.of(
                "error", "UNSUPPORTED_FORMAT",
                "message", "Unsupported file format '" + ext + "'. Expected .pdf or .csv"
            ));
        }

        Path tempPath = null;
        try {
            try {
                tempPath = Files.createTempFile("statement_upload_", ext,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException e) {
                tempPath = Files.createTempFile("statement_upload_", ext);
                File f = tempPath.toFile();
                f.setReadable(false, false);
                f.setReadable(true, true);
                f.setWritable(false, false);
                f.setWritable(true, true);
            }
            Files.write(tempPath, file.getBytes());

            String pythonBin = resolvePythonBinary();
            String script = resolveScriptPath();

            ProcessBuilder pb = new ProcessBuilder(
                pythonBin,
                script,
                "--file",
                tempPath.toAbsolutePath().toString()
            );

            if (password != null && !password.trim().isEmpty()) {
                pb.environment().put("CAS_PDF_PASSWORD", password.trim());
            }

            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout;
            String stderr;
            try (var stdoutIs = process.getInputStream();
                 var stderrIs = process.getErrorStream()) {
                stdout = new String(stdoutIs.readAllBytes(), StandardCharsets.UTF_8);
                stderr = new String(stderrIs.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Statement parser timed out after 60 seconds.");
                return ResponseEntity.internalServerError().body(Map.of(
                    "error", "UPLOAD_FAILED",
                    "message", "Statement ingestion timed out."
                ));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Statement parser CLI failed (exit {}): {}", exitCode, stderr);
                return ResponseEntity.internalServerError().body(Map.of(
                    "error", "UPLOAD_FAILED",
                    "message", "Statement ingestion failed due to an internal processing error."
                ));
            }

            if (stdout == null || stdout.trim().isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            ParsedEventDto[] dtoList = objectMapper.readValue(stdout, ParsedEventDto[].class);
            if (dtoList == null || dtoList.length == 0) {
                return ResponseEntity.ok(List.of());
            }

            List<TaxEvent> insertedEvents = ingestionUseCase.ingestParsedEvents(dtoList);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("parsed", dtoList.length);
            result.put("inserted", insertedEvents.size());
            result.put("reconciliation_status", "MATCHED");
            result.put("events", insertedEvents);
            return ResponseEntity.ok(result);

        } catch (ReconciliationGateFailureException e) {
            return ResponseEntity.status(422).body(Map.of(
                "error", "RECONCILIATION_GATE_FAILURE",
                "message", e.getMessage(),
                "assetResults", e.getReconciliationResult().assetResults()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(422).body(Map.of(
                "error", "TRANSACTION_ROW_CORRUPTED",
                "message", e.getMessage()
            ));
        } catch (IOException e) {
            log.error("IO error during statement processing: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "FILE_READ_FAILED",
                "message", "File reading failed"
            ));
        } catch (Exception e) {
            log.error("Unexpected error during statement ingestion: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "UPLOAD_FAILED",
                "message", "Statement ingestion failed due to an internal processing error."
            ));
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }
}
