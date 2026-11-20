# Portfolio OS Fast Task Runner
# Requires 'just' (sudo pacman -S just)

set dotenv-load := true

# Maven binary resolution (fallback to installed wrapper if mvn not in global PATH)
MVN := if `which mvn 2>/dev/null || true` != "" { "mvn" } else { env_var("HOME") + "/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn" }

# Default: List available commands
default:
    @just --list

# Comprehensive test suite (Core Node, Quant Sidecar, Web)
check:
    just test-core
    just test-quant
    just test-web

# Pre-release smoke test and validation gate for v4.1+
release-v4-1:
    ./scripts/pre_release_smoke_test.py

# Atomic SQLite ledger online backup & verification drill
drill-recovery:
    ./scripts/backup_ledger.sh

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
# Quant CAS Parser Tools (Python / On-demand CLI)
# -------------------------------------------------------------

# Set up isolated virtualenv and install dependencies using uv
setup-quant:
    cd quant-sidecar && uv venv && uv pip install -r requirements.txt

# Run Quant sidecar unit tests
test-quant:
    cd quant-sidecar && uv run python -m unittest discover -v -s tests

# -------------------------------------------------------------
# Code Quality: Lint & Format (Biome + Ruff)
# -------------------------------------------------------------

# Check and lint all frontend JS and Python code
lint:
    biome check core-node/src/main/resources/static/
    ruff check quant-sidecar/ scripts/

# Auto-format and fix all frontend JS and Python code
format:
    biome format --write *.json
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

# Run manual Gmail CAS email poller
sync:
    @./quant-sidecar/.venv/bin/python quant-sidecar/scripts/gmail_cas_poller.py

# Stop all backend services
down:
    systemctl --user stop portfolio-os.target portfolio-os-core.service

# Restart all backend services
restart:
    systemctl --user restart portfolio-os.target
    @just wait-ready

# Check service status and resource consumption
status:
    systemctl --user status portfolio-os-core.service

# Follow live logs from core service via journald
logs:
    journalctl --user -u portfolio-os-core -f

# Poll until both Core Node and Quant Sidecar endpoints are healthy
wait-ready:
    @./scripts/wait-ready.py

# -------------------------------------------------------------
# Global Cleanup
# -------------------------------------------------------------

# Clean all build outputs (Maven, Temp files)
clean: clean-core
    @echo "Cleaned all build directories."

# -------------------------------------------------------------
# AI Code Review Packages
# -------------------------------------------------------------

# Pack minimal production codebase for Gemini Pro review (XML & Markdown)
repomix:
    npx repomix
    npx repomix --style markdown -o repomix-minimal.md

# Pack native GPUI Cockpit codebase for review (Markdown & XML)
repomix-gpui:
    npx -y repomix --config repomix.gpui.json
    npx -y repomix --config repomix.gpui.json --style xml --output repomix-gpui.xml

# Pack targeted core money-path and business logic (avoids context truncation)
repomix-audit-core:
    npx -y repomix --config repomix.audit-core.json --no-git-sort-by-changes
    npx -y repomix --config repomix.audit-core.json --no-git-sort-by-changes --style xml --output repomix-audit-core.xml

# Pack ultra-compact money path bundle (~66k tokens, zero risk of context cutoff)
repomix-money-path:
    npx -y repomix --config repomix.money-path.json --no-git-sort-by-changes
    npx -y repomix --config repomix.money-path.json --no-git-sort-by-changes --style xml --output repomix-money-path.xml

# Pack remaining tail bundle (~61k tokens, covers scripts, MCP, App.svelte, api.ts)
repomix-unverified-tail:
    npx -y repomix --config repomix.unverified-tail.json --no-git-sort-by-changes
    npx -y repomix --config repomix.unverified-tail.json --no-git-sort-by-changes --style xml --output repomix-unverified-tail.xml

# Atomic local SQLite backup of primary tax ledger
backup:
    @mkdir -p data/backups
    sqlite3 data/tax_ledger.db ".backup 'data/backups/tax_ledger_$$(date +%F).db'"

