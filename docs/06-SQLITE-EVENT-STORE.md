# SQLite Event Store & Persistence Subsystem

The SQLite event store provides immutable, append-only persistence for all transaction events in Portfolio OS. It features cryptographic HMAC-SHA256 event hash chaining to guarantee ledger tamper resistance, paired with HTTP security interceptors enforcing mandatory API token authentication.

---

## 1. Architecture Map

```
core-node/src/main/java/com/portfolioos/core/
├── persistence/
│   └── SqliteEventStore.java                     # SQLite JDBC driver wrapper & HMAC hash chain engine
├── security/
│   ├── SecurityConfig.java                       # WebMvcConfigurer interceptor registration
│   └── SecurityInterceptor.java                  # API_AUTH_TOKEN HTTP request validator
└── ports/
    └── EventStorePort.java                       # Event store domain interface
```

SQLite Database File: `data/tax_ledger.db`

```sql
CREATE TABLE IF NOT EXISTS tax_events (
  id TEXT PRIMARY KEY,
  asset_id TEXT NOT NULL,
  asset_name TEXT NOT NULL,
  isin TEXT,
  event_type TEXT NOT NULL,
  event_date TEXT NOT NULL,
  units TEXT NOT NULL,
  price_per_unit TEXT NOT NULL,
  gross_amount TEXT NOT NULL,
  source_document_id TEXT NOT NULL,
  ingested_at TEXT NOT NULL,
  previous_hash TEXT NOT NULL,
  event_hash TEXT NOT NULL
);
```

---

## 2. Data Flow: Cryptographic Event Append & Hash Chain Generation

When a new transaction event is saved via `SqliteEventStore.appendEvents(events)`:

```
[StatementIngestionUseCase.java:L57] appendEvents(taxEvents)
       |
       v  1. Acquires synchronized lock & opens SQLite connection
[SqliteEventStore.java:L153] appendEvents()
       |
       |----> 2. Fetches latest event_hash from database
       |      [L103] getLatestEventHash() -> Returns "GENESIS" if empty
       |
       |----> 3. For each incoming event:
       |         a. Checks for existing identical event (deduplication query)
       |         b. Computes canonical string:
       |            raw = prevHash + "|" + id + "|" + assetId + "|" + isin + "|" + assetName + "|" +
       |                  eventType + "|" + eventDate + "|" + units(8 decimals) + "|" +
       |                  pricePerUnit(8 decimals) + "|" + grossAmount(8 decimals) + "|" + sourceDocId
       |         c. Calculates HMAC-SHA256 signature using LEDGER_HMAC_SECRET key
       |         d. Inserts record with previous_hash and event_hash
       |         e. Sets prevHash = eventHash for next iteration
       |
       v  4. Commits SQLite database transaction
[SqliteEventStore.java:L205] conn.commit()
```

---

## 3. Business Logic Inventory

### 3.1. SqliteEventStore HMAC-SHA256 Hash Chaining
* **Source**: [`SqliteEventStore.java:L121-L144`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java#L121-L144)
* **Logic**:
  - Requires `LEDGER_HMAC_SECRET` environment variable at initialization ([`L39-L42`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java#L39-L42)). Throws `IllegalStateException` if missing or blank.
  - Numbers (`units`, `pricePerUnit`, `grossAmount`) are normalized to **8 decimal places** (`toCanonicalString`) before hashing to eliminate floating-point string formatting discrepancies.
  - Computes `Mac.getInstance("HmacSHA256")` and formats as a 64-character hexadecimal string.

### 3.2. Ledger Integrity Verification
* **Source**: [`SqliteEventStore.java:L253-L295`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java#L253-L295)
* **Logic**: Iterates sequentially through all `tax_events` ordered by `ingested_at ASC, id ASC`. Re-calculates the expected HMAC signature for each event using the previous event's hash. Returns `false` immediately if any `previous_hash` mismatch or corrupted payload signature is detected.

### 3.3. SecurityInterceptor Token Validation
* **Source**: [`SecurityInterceptor.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/security/SecurityInterceptor.java)
* **Logic**: Intercepts all incoming REST requests (exempting CORS `OPTIONS` preflight requests). Compares client header `X-Api-Auth-Token` or `Authorization: Bearer <token>` against environment variable `API_AUTH_TOKEN` using `java.security.MessageDigest.isEqual` for **constant-time comparison** against timing side-channel attacks.

---

## 4. Test Coverage Map

Empirical test count obtained via `grep -rc "@Test" core-node/src/test/java`:

| Test Class | Path | Real `@Test` Count | Verified Behaviors |
| :--- | :--- | :---: | :--- |
| `SqliteEventStoreTest` | [`SqliteEventStoreTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/persistence/SqliteEventStoreTest.java) | **1** | Event append, hash chain calculation, and ledger integrity verification |
| `SecurityInterceptorTest` | [`SecurityInterceptorTest.java`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/test/java/com/portfolioos/core/security/SecurityInterceptorTest.java) | **3** | Valid token acceptance, missing token 401 rejection, constant-time comparison |

---

## 5. Known Issues & Historical Fallback Flags

1. **Mandatory Environment Variables**:
   - `LEDGER_HMAC_SECRET`: Required by `SqliteEventStore`. If missing, server startup fails with `IllegalStateException`.
   - `API_AUTH_TOKEN`: Required by `SecurityInterceptor`. If missing, incoming HTTP requests throw an `IllegalStateException`.
2. **Rehash Ledger Utility**: `rehashLedgerChain()` ([`L297-L345`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java#L297-L345)) allows administrative re-signing of the database chain if secret keys or canonical schemas are migrated.
