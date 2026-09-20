package com.portfolioos.core.config;

import java.io.File;

/**
 * Resolves SQLite and DuckDB database paths canonically across runtime environments.
 * Prevents creation of or binding to stale shadow databases in subproject working directories
 * (e.g., when launched from core-node/ via Maven or Just).
 *
 * Idempotent: Can be safely called multiple times on the same path.
 */
public final class DbPathResolver {

    private DbPathResolver() {}

    public static String resolveDatabasePath(String configuredPath, String envVarName) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return configuredPath;
        }

        // Ephemeral test databases inside target/test-db or in-memory databases must NEVER be overridden by environment variables!
        if (":memory:".equals(configuredPath)) {
            return configuredPath;
        }
        if (configuredPath.startsWith("target/") || configuredPath.contains("test-db")) {
            return new File(configuredPath).toPath().toAbsolutePath().normalize().toString();
        }

        if (envVarName != null && !envVarName.isBlank()) {
            String envVal = System.getenv(envVarName);
            if (envVal != null && !envVal.isBlank()) {
                return normalizeIfFile(envVal.trim());
            }
        }

        // If running from subproject root (e.g. core-node/), canonical data/ lives at ../data/
        // Checked via ../data directory or presence of root justfile (for clean checkouts prior to DB generation)
        if (new File("pom.xml").exists() && (new File("../data").isDirectory() || new File("../justfile").exists())) {
            File rootFile = new File("..", configuredPath);
            return rootFile.toPath().toAbsolutePath().normalize().toString();
        }

        // Check if file exists directly from current working directory
        File f = new File(configuredPath);
        if (f.exists()) {
            return f.toPath().toAbsolutePath().normalize().toString();
        }

        // Fallback check parent directory
        File parentTarget = new File("..", configuredPath);
        if (parentTarget.exists()) {
            return parentTarget.toPath().toAbsolutePath().normalize().toString();
        }

        return f.toPath().toAbsolutePath().normalize().toString();
    }

    private static String normalizeIfFile(String path) {
        if (path == null || path.isBlank() || ":memory:".equals(path) || path.startsWith("jdbc:")) {
            return path;
        }
        return new File(path).toPath().toAbsolutePath().normalize().toString();
    }
}
