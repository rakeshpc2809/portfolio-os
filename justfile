# Portfolio OS Fast Task Runner
# Requires 'just' (sudo pacman -S just)

set dotenv-load := true

# Maven binary resolution (fallback to installed wrapper if mvn not in global PATH)
MVN := if `which mvn 2>/dev/null || true` != "" { "mvn" } else { env_var("HOME") + "/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn" }

# Default: List available commands
default:
    @just --list

# Comprehensive multi-platform test suite (Core Node, Quant Sidecar, TUI, Mobile, Web)
check:
    just test-core
    just test-quant
    just test-tui
    just test-mobile
    just test-web

# Pre-release smoke test and validation gate for v4.1+
release-v4-1:
    ./scripts/pre_release_smoke_test.py

# Chaos & Disaster Recovery Drill (Litestream WAL + Google Sheets Zero-Knowledge restoration)
drill-recovery:
    ./scripts/chaos_recovery_drill.py

# -------------------------------------------------------------
# Mobile App (Android / Kotlin)
# -------------------------------------------------------------

# Run Android unit test suite
test-mobile:
    cd mobile-app && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" ./gradlew testDebugUnitTest

# Build debug APK with parallel execution and build caching
build-mobile:
    cd mobile-app && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" ./gradlew assembleDebug --build-cache --parallel

# Build and install debug APK onto connected device / emulator
install-mobile:
    cd mobile-app && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" ./gradlew installDebug --build-cache --parallel

# Clean Gradle build cache and build directory
clean-mobile:
    cd mobile-app && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" ./gradlew clean

# -------------------------------------------------------------
# Backend Core Node (Spring Boot / Java 26)
# -------------------------------------------------------------

# Fast multi-threaded compilation across all CPU cores (skipping tests)
build-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" {{MVN}} compile -T 1C

# Build full executable JAR package (multi-threaded, skip tests for speed)
package-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" {{MVN}} package -T 1C -DskipTests

# Run test suite with multi-threaded executor
test-core:
    cd core-node && env -u _JAVA_OPTIONS -u SQLITE_PATH -u DUCKDB_PATH JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" {{MVN}} test -T 1C

# Run Spring Boot app locally in foreground
run-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" {{MVN}} spring-boot:run

# Clean Maven target directory
clean-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/home/rakeshpc/.jdks/jdk-21}" {{MVN}} clean


# -------------------------------------------------------------
# Frontend Dashboard (JavaScript / Bun)
# -------------------------------------------------------------

# Build modern Svelte 5 Web Cockpit with Vite
build-web:
    cd web && npm run build

# Serve static web cockpit locally with Bun
serve-web:
    cd core-node/src/main/resources/static && bun x serve -p 3000 .

# Validate static web cockpit contract and export integrity
test-web: build-web
    node core-node/src/test/resources/static-test.mjs

# -------------------------------------------------------------
# Quant Sidecar (Python / FastAPI / Direct IPC)
# -------------------------------------------------------------

# Set up isolated virtualenv and install dependencies at ultra-fast speeds using uv
setup-quant:
    cd quant-sidecar && uv venv && uv pip install -r requirements.txt

# Run Quant Sidecar with uv in foreground
run-quant:
    cd quant-sidecar && API_AUTH_TOKEN="${API_AUTH_TOKEN}" uv run uvicorn app:app --host 127.0.0.1 --port 8000 --reload

# Run Quant sidecar unit tests
test-quant:
    cd quant-sidecar && uv run python -m unittest discover -v -s tests

# Refresh historical NAV export (Parquet) for HRP allocator
refresh-nav-export:
    cd quant-sidecar && uv run python scripts/backfill_nav_history.py

# Run Portfolio OS Model Context Protocol (MCP) Server (stdio)
run-mcp:
    cd quant-sidecar && uv run python mcp_server.py


# -------------------------------------------------------------
# Portfolio OS TUI Terminal Cockpit (Textual)
# -------------------------------------------------------------

# Build Rust TUI release binary (stripped, ~2.9 MB)
build-tui:
    cargo build --release --manifest-path tui/Cargo.toml

# Launch Portfolio OS Terminal HUD (Rust/Ratatui)
tui: build-tui
    ./tui/target/release/portfolio-os-tui

# Run Rust TUI unit tests
test-tui:
    cargo test --manifest-path tui/Cargo.toml

# Launch full Zellij Cockpit (Backend + Quant + TUI HUD in split panes)
cockpit:
    zellij --layout tui/portfolio_os.kdl

# Launch Zellij TUI Dev Studio (Textual console + hot reload TUI)
cockpit-dev:
    zellij --layout tui/portfolio_os_dev.kdl

# -------------------------------------------------------------
# Code Quality: Lint & Format (Biome + Ruff)
# -------------------------------------------------------------

# Check and lint all frontend JS and Python code
lint:
    biome check core-node/src/main/resources/static/
    ruff check quant-sidecar/ scripts/

# Auto-format and fix all frontend JS and Python code
format:
    biome format --write core-node/src/main/resources/static/
    ruff format quant-sidecar/ scripts/
    ruff check quant-sidecar/ scripts/ --fix

# -------------------------------------------------------------
# Full Stack Systemd Service Controls (systemctl --user)
# -------------------------------------------------------------

# Install systemd user service units into ~/.config/systemd/user/
install-service:
    ./scripts/install-systemd-units.sh

# Start all backend services via systemd user manager and wait for readiness
up:
    @if [ ! -f core-node/target/core-node-3.0.0.jar ]; then \
        echo "[*] core-node-3.0.0.jar not found, packaging now..."; \
        just package-core; \
    fi
    systemctl --user start portfolio-os.target
    @just wait-ready

# Stop all backend services
down:
    systemctl --user stop portfolio-os.target portfolio-os-core.service portfolio-os-quant.service

# Restart all backend services
restart:
    systemctl --user restart portfolio-os.target
    @just wait-ready

# Check service status and resource consumption
status:
    systemctl --user status portfolio-os-core.service portfolio-os-quant.service

# Follow live logs from both services via journald
logs:
    journalctl --user -u portfolio-os-core -u portfolio-os-quant -f

# Poll until both Core Node and Quant Sidecar endpoints are healthy
wait-ready:
    @./scripts/wait-ready.py

# -------------------------------------------------------------
# Global Cleanup
# -------------------------------------------------------------

# Clean all build outputs (Maven, Gradle, Temp files)
clean: clean-mobile clean-core
    @echo "Cleaned all build directories."

# -------------------------------------------------------------
# AI Code Review Packages
# -------------------------------------------------------------

# Pack minimal production codebase for Gemini Pro review (XML & Markdown)
repomix:
    npx repomix
    npx repomix --style markdown -o repomix-minimal.md

# Atomic local SQLite backup of primary tax ledger
backup:
    @mkdir -p data/backups
    sqlite3 data/tax_ledger.db ".backup 'data/backups/tax_ledger_$$(date +%F).db'"

