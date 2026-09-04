# Portfolio OS Fast Task Runner
# Requires 'just' (sudo pacman -S just)

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
# Backend Core Node (Spring Boot / Java 21)
# -------------------------------------------------------------

# Fast multi-threaded compilation across all CPU cores (skipping tests)
build-core:
    cd core-node && mvn compile -T 1C

# Build full executable JAR package (multi-threaded, skip tests for speed)
package-core:
    cd core-node && mvn package -T 1C -DskipTests

# Run test suite with multi-threaded executor
test-core:
    cd core-node && mvn test -T 1C

# Run Spring Boot app locally
run-core:
    cd core-node && mvn spring-boot:run

# Clean Maven target directory
clean-core:
    cd core-node && mvn clean

# -------------------------------------------------------------
# Frontend Dashboard (JavaScript / Bun)
# -------------------------------------------------------------

# Serve static web cockpit locally with Bun
serve-web:
    cd core-node/src/main/resources/static && bun x serve -p 3000 .

# -------------------------------------------------------------
# Quant Sidecar (Python / FastAPI / Flight RPC)
# -------------------------------------------------------------

# Set up isolated virtualenv and install dependencies at ultra-fast speeds using uv
setup-quant:
    cd quant-sidecar && uv venv && uv pip install -r requirements.txt

# Run Quant Sidecar with uv
run-quant:
    cd quant-sidecar && uv run uvicorn app:app --host 127.0.0.1 --port 8000 --reload

# -------------------------------------------------------------
# Portfolio OS TUI Terminal Cockpit (Textual)
# -------------------------------------------------------------

# Launch Portfolio OS Terminal HUD
tui:
    cd tui && python3 portfolio_os_tui.py

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
# Full Stack Docker / Podman Controls
# -------------------------------------------------------------

# Start all backend services in detached mode
up:
    podman-compose up -d --build

# Stop all backend services
down:
    podman-compose down

# Follow backend logs
logs:
    podman-compose logs -f

# -------------------------------------------------------------
# Global Cleanup
# -------------------------------------------------------------

# Clean all build outputs (Maven, Gradle, Temp files)
clean: clean-mobile clean-core
    @echo "Cleaned all build directories."

