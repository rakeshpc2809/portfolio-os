# Portfolio OS Fast Task Runner
# Requires 'just' (sudo pacman -S just)

set dotenv-load := true

# Maven binary resolution (fallback to installed wrapper if mvn not in global PATH)
MVN := if `which mvn 2>/dev/null || true` != "" { "mvn" } else { env_var("HOME") + "/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn" }

# Default: List available commands
default:
    @just --list

# -------------------------------------------------------------
# Mobile App (Android / Kotlin)
# -------------------------------------------------------------

# Build debug APK with parallel execution and build caching
build-mobile:
    cd mobile-app && ./gradlew assembleDebug --build-cache --parallel

# Build and install debug APK onto connected device / emulator
install-mobile:
    cd mobile-app && ./gradlew installDebug --build-cache --parallel

# Clean Gradle build cache and build directory
clean-mobile:
    cd mobile-app && ./gradlew clean

# -------------------------------------------------------------
# Backend Core Node (Spring Boot / Java 26)
# -------------------------------------------------------------

# Fast multi-threaded compilation across all CPU cores (skipping tests)
build-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}" {{MVN}} compile -T 1C

# Build full executable JAR package (multi-threaded, skip tests for speed)
package-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}" {{MVN}} package -T 1C -DskipTests

# Run test suite with multi-threaded executor
test-core:
    cd core-node && env -u _JAVA_OPTIONS -u SQLITE_PATH -u DUCKDB_PATH JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}" {{MVN}} test -T 1C

# Run Spring Boot app locally in foreground
run-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}" {{MVN}} spring-boot:run

# Clean Maven target directory
clean-core:
    cd core-node && env -u _JAVA_OPTIONS JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}" {{MVN}} clean

# Query available LLM tool-calling schemas from Core Node
agent-tools:
    @curl -s -H "X-Api-Key: ${API_AUTH_TOKEN}" http://localhost:8080/api/v1/agent/tools | jq .

# -------------------------------------------------------------
# Frontend Dashboard (JavaScript / Bun)
# -------------------------------------------------------------

# Serve static web cockpit locally with Bun
serve-web:
    cd core-node/src/main/resources/static && bun x serve -p 3000 .

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

# Query HRP allocator dynamic weights (advisory diagnostic)
alloc-hrp:
    @curl -s -X POST http://127.0.0.1:8000/api/v1/allocator/hrp \
      -H "Content-Type: application/json" \
      -H "X-Api-Auth-Token: ${API_AUTH_TOKEN}" \
      -d '{"mode":"INTRA_BUCKET"}' | jq .

# -------------------------------------------------------------
# Portfolio OS TUI Terminal Cockpit (Textual)
# -------------------------------------------------------------

# Launch Portfolio OS Terminal HUD
tui:
    cd tui && python3 portfolio_os_tui.py

# Run TUI unit & modal lifecycle tests
test-tui:
    cd tui && python3 -m unittest discover -v -s tests

# Launch TUI with hot-reloading DevTools
tui-dev:
    cd tui && textual run --dev portfolio_os_tui.py

# Open Textual live inspection console in standalone pane
tui-console:
    textual console

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
    ruff check quant-sidecar/ tui/

# Auto-format and fix all frontend JS and Python code
format:
    biome format --write core-node/src/main/resources/static/
    ruff format quant-sidecar/ tui/
    ruff check quant-sidecar/ tui/ --fix

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
