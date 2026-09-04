package com.portfolioos.core.backup;

import java.io.IOException;
import java.util.List;

/**
 * Interface representing external Google Sheets backup operations.
 * Allows decoupling HTTP transport / mock testing from orchestration logic.
 */
public interface GoogleSheetsClient {
    /**
     * Appends a batch of rows to the target spreadsheet tab.
     *
     * @param spreadsheetId target Google Sheet file ID
     * @param range target tab range (e.g. "ledger_events!A:H")
     * @param rows list of row values (each row being a list of objects)
     * @return number of rows appended
     * @throws IOException if network or API failure occurs
     */
    int appendRows(String spreadsheetId, String range, List<List<Object>> rows) throws IOException;

    /**
     * Optional hook to inspect sleep/pacing invocations during test runs.
     *
     * @param millis time to sleep between batches
     * @throws InterruptedException if thread is interrupted
     */
    default void pace(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }
}
