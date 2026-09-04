package com.portfolioos.core.controllers;

import com.portfolioos.core.backup.GoogleSheetsBackupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller exposing security-gated backup endpoints under /api/v1/backup/sheets/*
 * Automatically protected by SecurityInterceptor (requires X-Api-Auth-Token / Bearer).
 */
@RestController
@RequestMapping("/api/v1/backup/sheets")
public class SheetsBackupController {

    private final GoogleSheetsBackupService backupService;

    public SheetsBackupController(GoogleSheetsBackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncIncremental() {
        GoogleSheetsBackupService.SyncResult result = backupService.syncIncrementalEvents();
        return ResponseEntity.ok(Map.of(
            "status", result.success() ? "SUCCESS" : "FAILED",
            "rows_synced", result.rowsSynced(),
            "message", result.message()
        ));
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seedAll() {
        GoogleSheetsBackupService.SyncResult result = backupService.seedAllLedgerEvents();
        return ResponseEntity.ok(Map.of(
            "status", result.success() ? "SUCCESS" : "FAILED",
            "rows_synced", result.rowsSynced(),
            "message", result.message()
        ));
    }
}
