# GraalVM Native Image Base Runtime Spike

## Verified Findings (2026-09-13)
1. `gcr.io/distroless/base-nossl-debian12:nonroot` and `gcr.io/distroless/cc-debian12:nonroot`:
   - **FAIL (exit 127)**: Missing `libz.so.1`. Native Java HTTP/TLS clients dynamically link to zlib for compression and fail immediately on startup.
2. `debian:12-slim` (+ `zlib1g`, `ca-certificates`):
   - **PASS**: Successfully links and completes strict TLS handshake against `https://www.amfiindia.com/` (HTTP 200) running unprivileged as `USER 65532:65532`. Total image size: 128 MB.
3. `alpine:latest` (+ `gcompat`, `zlib`, `ca-certificates`):
   - **PASS** on HTTP/TLS probe (49.9 MB), but **UNPROVEN / HIGH RISK** for DuckDB/SQLite JNI shared libraries (`libduckdb_java.so`, `libsqlitejdbc.so`) due to musl vs glibc symbol gaps.
