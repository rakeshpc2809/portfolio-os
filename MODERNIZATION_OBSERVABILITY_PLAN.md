# Modern Developer Tooling & Observability Implementation Plan

This document outlines the complete modernization roadmap for the development environment, build systems, code quality pipeline, test coverage, and observability stack across projects like `portfolio-os`.

---

## 1. Toolchain & Runtime Modernization (Completed)

| Layer | Legacy Approach | Modern Standard | Status |
| :--- | :--- | :--- | :--- |
| **JS/TS Runtime & Bundler** | Node.js + ts-node | **Bun 1.4** | Installed (`bun 1.4.0`) |
| **JS/TS Formatter & Linter** | ESLint + Prettier | **Biome** | Installed (`biome 2.5.10`) |
| **Python Dependency & Env** | pip + virtualenv | **uv** | Installed (`uv 0.5.9`) |
| **Python Formatter & Linter**| Flake8 + Black + isort | **Ruff** | Installed (`ruff 0.16.4`) |
| **Task / Command Runner** | npm scripts / Make | **Just** | Installed (`just 1.58.0`) |
| **Container Engine** | Docker Daemon | **Podman 6.1.0** | Installed (`podman 6.1.0` + `podman-compose`) |

---

## 2. Build & Container Acceleration (`portfolio-os`)

### A. Mobile Build Acceleration (`mobile-app`)
* **JVM Heap & GC:** Configured `6144m` (6 GB) with `ParallelGC`.
* **Multi-Core Parallelism:** `org.gradle.parallel=true` (utilizes all 18 CPU cores).
* **Caching:** `org.gradle.caching=true` + `org.gradle.configuration-cache=true` + `org.gradle.vfs.watch=true`.
* **Result:** Re-builds reduced from minutes to 5–15 seconds.

### B. Core Node (Spring Boot / Java 21)
* **Multi-threaded Compilation:** `mvn -T 1C` enabled.
* **Optional Daemon:** `mvnd` (Maven Daemon via `paru -S mvnd`) for resident JVM builds.

### C. Docker Builds (`quant-sidecar`)
* Integrated `uv` in `quant-sidecar/Dockerfile` via `COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv`.
* **Result:** Container dependency installation dropped from ~2 minutes to ~5 seconds.

---

## 3. Code Quality & Security (Shift-Left SAST)

Replacing heavy centralized servers (like SonarQube) with ultra-fast local CLI scanners:

```
                      MODERN SAST & QUALITY WORKFLOW
┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐
│  Biome / Ruff  │ ──► │    Semgrep     │ ──► │    Gitleaks    │ ──► │     Trivy      │
│  (Style/Lints) │     │ (Logic & OWASP)│     │(Secret Leaks)  │     │ (CVE Security) │
└────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘
```

### Tools to Enable:
1. **Semgrep:** Local static analysis & OWASP vulnerability detection (`uvx semgrep scan --config auto`).
2. **Gitleaks:** Secret and API key leak detection (`gitleaks detect`).
3. **Trivy:** Container image & dependency CVE scanner (`trivy fs .`).

---

## 4. Code Coverage Automation

Standardize coverage across languages with automated reporting:

| Component | Technology | Tool | Output / Report |
| :--- | :--- | :--- | :--- |
| **`core-node`** | Java 21 / Spring Boot | **JaCoCo** | `core-node/target/site/jacoco/index.html` |
| **`mobile-app`** | Kotlin / Android Compose | **Kover** (JetBrains) | `mobile-app/app/build/reports/kover/html/` |
| **`quant-sidecar`**| Python / FastAPI | **pytest-cov** | `quant-sidecar/htmlcov/index.html` |
| **Web Dashboard** | JavaScript / Bun | **`bun test --coverage`**| Terminal + LCOV output |

---

## 5. Observability: Metrics, Logs & Tracing

Moving from the 4-server legacy stack (Prometheus + Loki + Jaeger + Grafana) to a lightweight, OpenTelemetry-native architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   MODERN OBSERVABILITY ARCHITECTURE                    │
├───────────────────────────────┬────────────────────────────────────────┤
│ Layer                         │ Modern Implementation                  │
├───────────────────────────────┼────────────────────────────────────────┤
│ **Instrumentation Protocol**  │ OpenTelemetry (OTel standard)          │
│ **Spring Boot Exporter**      │ Micrometer OTLP Registry               │
│ **Python FastAPI Exporter**   │ `opentelemetry-instrumentation-fastapi`│
│ **Lightweight Storage**       │ VictoriaMetrics (Metrics) +            │
│                               │ VictoriaLogs (Logs)                    │
│ **All-in-One UI Alternative** │ SigNoz (Single-container OTel APM)     │
└───────────────────────────────┴────────────────────────────────────────┘
```

### Phased Observability Implementation:
1. **Phase 1 (OpenTelemetry Instrumentation):**
   * Add Micrometer OTLP dependency in `core-node/pom.xml`.
   * Add OpenTelemetry auto-instrumentor in `quant-sidecar/requirements.txt`.
2. **Phase 2 (Single-Container Collector / Backend):**
   * Add **VictoriaMetrics** & **VictoriaLogs** (or **SigNoz**) into `docker-compose.yml`.
   * Export all metrics/logs over OTLP port `4317`/`4318`.

---

## 6. Unified Execution via `justfile`

The root `justfile` orchestrates all of the above workflows in one place:

```just
# -------------------------------------------------------------
# Development & Builds
# -------------------------------------------------------------
build-mobile:
    cd mobile-app && ./gradlew assembleDebug --build-cache --parallel

build-core:
    cd core-node && mvn compile -T 1C

run-quant:
    cd quant-sidecar && uv run uvicorn app:app --reload

serve-web:
    cd core-node/src/main/resources/static && bun x serve -p 3000 .

# -------------------------------------------------------------
# Quality, Lints & Security (Shift-Left)
# -------------------------------------------------------------
format:
    biome format --write core-node/src/main/resources/static/
    ruff format quant-sidecar/
    ruff check quant-sidecar/ --fix

audit:
    uvx semgrep scan --config auto
    gitleaks detect
    trivy fs .

# -------------------------------------------------------------
# Coverage Reports
# -------------------------------------------------------------
coverage:
    cd core-node && mvn test jacoco:report -T 1C
    cd mobile-app && ./gradlew koverHtmlReport
    cd quant-sidecar && uv run pytest --cov=. --cov-report=html

# -------------------------------------------------------------
# Observability & Containers
# -------------------------------------------------------------
up:
    docker compose up -d --build

down:
    docker compose down
```
