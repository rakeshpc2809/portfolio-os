# Android App Subsystem (Kotlin / Jetpack Compose / Glance Widget)

The mobile client is a native Kotlin Android application built with Jetpack Compose, Material 3 design tokens, Biometric Prompt authentication, Jetpack Glance Home Screen Widgets, and an autonomous multi-network sync client.

---

## 1. Architecture Map

```
mobile-app/app/src/main/java/com/portfolioos/mobile/
├── MainActivity.kt                                # Root ComponentActivity & Navigation state
├── api/
│   └── SyncApiClient.kt                           # Multi-network Retrofit API client with offline fallbacks
├── auth/
│   └── BiometricAuthManager.kt                    # Fingerprint & Face Unlock manager (BiometricPrompt)
├── data/
│   ├── SnapshotCacheManager.kt                    # SharedPreferences JSON offline snapshot store
│   └── nav/
│       └── AmfiDirectFetcher.kt                   # Direct cellular AMFI NAV fetcher when server is offline
├── model/
│   └── SyncModels.kt                              # Data Transfer Objects (SyncSnapshot, HoldingsDto, LotDto)
├── ui/
│   ├── DashboardScreen.kt                         # Main dashboard view with Pull-to-Refresh
│   ├── LockScreenGate.kt                          # Biometric authentication gate composable
│   ├── PortfolioCharts.kt                         # Custom Canvas allocation ring & net worth trend charts
│   ├── SimulatorScreen.kt                         # Trade simulation input form & result preview
│   ├── components/
│   │   └── StateCard.kt                           # Status indicator cards & AMFI freshness banner
│   └── theme/
│       ├── ColorTokens.kt                         # Curated HSL dark mode palette
│       ├── ShapeTokens.kt                         # Rounded border shapes
│       ├── SpacingTokens.kt                       # Standard padding tokens
│       ├── Theme.kt                               # Portfolio OS Compose MaterialTheme wrapper
│       └── TypographyTokens.kt                    # Inter & JetBrains Mono font definitions
├── util/
│   └── FormatUtils.kt                             # INR currency & percentage formatters
└── widget/
    └── PortfolioGlanceWidget.kt                   # Jetpack Glance home screen widget
```

---

## 2. Data Flow: Snapshot Synchronization & Offline Fallback Chain

When the user pulls down to refresh or opens the mobile application, `SyncApiClient.fetchSnapshotWithFallback()` attempts synchronization across five ordered network targets:

```
[DashboardScreen.kt] -> Pull-To-Refresh Trigger
       |
       v  1. Invokes multi-network sync fallback
[SyncApiClient.kt:L59] fetchSnapshotWithFallback(context)
       |
       |----> 2. Checks Custom Server URL (if set in settings)
       |      [L64-L73] GET <customUrl>/api/v1/sync/snapshot
       |
       |----> 3. Checks USB Loopback (adb reverse port forwarding)
       |      [L76-L79] GET http://127.0.0.1:8080/api/v1/sync/snapshot
       |
       |----> 4. Checks Android Emulator Network Loopback
       |      [L82-L85] GET http://10.0.2.2:8080/api/v1/sync/snapshot
       |
       |----> 5. Checks Local Wi-Fi LAN IP
       |      [L88-L91] GET http://192.168.1.13:8080/api/v1/sync/snapshot
       |
       `----> 6. Offline Fallback Mode
              [L93-L109]
              - Loads local cached snapshot from SnapshotCacheManager
              - Attempts direct AMFI NAV download via AmfiDirectFetcher (cellular)
              - If AMFI NAVs fetched:
                    Updates asset values with cellular AMFI NAVs
                    Sets isFullLedgerSync = false & updates amfi_nav_timestamp
                    Displays amber banner: "Cellular AMFI NAV Update (Server Disconnected)"
              - If Airplane Mode / Completely Offline:
                    Preserves frozen cache & sets isFullyOffline = true
                    Displays banner: "Autonomous Offline Mode (Cache Frozen)"
```

---

## 3. Business Logic Inventory

### 3.1. LockScreenGate & BiometricAuthManager
* **Source**: [`LockScreenGate.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/LockScreenGate.kt) & [`BiometricAuthManager.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/auth/BiometricAuthManager.kt)
* **Logic**: Wraps the root UI with a biometric lock screen gate. Prompts for fingerprint or device PIN authentication on app launch or when returning from background. Disables access to financial data until authentication succeeds.

### 3.2. Jetpack Glance Home Screen Widget
* **Source**: [`PortfolioGlanceWidget.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt)
* **Logic**: Periodically reads the latest cached `SyncSnapshot` from `SnapshotCacheManager` and renders an Android Home Screen Widget displaying Net Worth, Day's Change, and Rebalance Trigger Status without opening the app.

### 3.3. Dual Timestamping & Offline AMFI Freshness
* **Source**: [`SyncApiClient.kt:L93-L106`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt#L93-L106)
* **Logic**: Maintains two distinct sync timestamps:
  - `full_ledger_sync_timestamp`: Updated only when connected to the Core Node REST server.
  - `amfi_nav_timestamp`: Updated whenever direct AMFI NAVs are downloaded over cellular.
  - When operating in cellular offline mode, an amber warning banner notifies the user that NAVs are live but ledger events reflect the last server sync date.

---

## 4. Test Coverage Map

Empirical test count obtained via `grep -rc "@Test" mobile-app/app/src/test/java`:

| Test Class | Path | Real `@Test` Count | Verified Behaviors |
| :--- | :--- | :---: | :--- |
| `RebalanceWaterfallUnitTest` | [`RebalanceWaterfallUnitTest.kt`](file:///home/rakeshpc/.gemini/antigravity/worktrees/portfolio-os/generate_portfolio_os_docs/mobile-app/app/src/test/java/com/portfolioos/mobile/RebalanceWaterfallUnitTest.kt) | **4** | Client-side rebalance waterfall preview calculation, tax drag verification, DTO parsing |

---

## 5. Known Issues & Historical Fallback Flags

1. **Snake-case / Camel-case DTO Incompatibility (Resolved)**: In prior builds, server DTO key format mismatches caused `TypeError` crashes on mobile data mapping. Model classes in `SyncModels.kt` were updated with `@SerializedName` annotations handling both `snake_case` and `camelCase` keys.
2. **ADB Reverse Port Forwarding Requirement**: For USB debugging sync (`http://127.0.0.1:8080/`), `adb reverse tcp:8080 tcp:8080` must be active on the host development machine.
