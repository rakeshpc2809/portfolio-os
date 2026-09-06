package com.portfolioos.core.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Production implementation of GoogleSheetsClient.
 * Loads service account credentials from credentials/sheets_service_account.json
 * or path configured in GOOGLE_SHEETS_SERVICE_ACCOUNT_KEY_PATH.
 */
@Component
@ConditionalOnMissingBean(type = "com.portfolioos.core.backup.MockGoogleSheetsClient")
public class DefaultGoogleSheetsClient implements GoogleSheetsClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleSheetsClient.class);

    private final String keyPath;

    public DefaultGoogleSheetsClient(
        @Value("${google.sheets.service-account-key-path:}") String keyPath
    ) {
        String envPath = System.getenv("GOOGLE_SHEETS_SERVICE_ACCOUNT_KEY_PATH");
        if (envPath != null && !envPath.isBlank()) {
            this.keyPath = envPath;
        } else if (keyPath != null && !keyPath.isBlank()) {
            this.keyPath = keyPath;
        } else {
            this.keyPath = "credentials/sheets_service_account.json";
        }
    }

    @Override
    public int appendRows(String spreadsheetId, String range, List<List<Object>> rows) throws IOException {
        File keyFile = new File(keyPath);
        if (!keyFile.exists()) {
            throw new IOException("Google Sheets backup key file not found at '" + keyFile.getAbsolutePath() + "'. Cannot perform off-site backup.");
        }

        // Active production runtime requires Google Sheets API v4 transport
        throw new IOException("Google Sheets API v4 transport is not configured on this environment. Cannot perform off-site backup.");
    }
}
