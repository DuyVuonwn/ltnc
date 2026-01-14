package com.ltnc.model;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    // =========================
    // GET DATABASE URL
    // =========================
    private static String getDatabaseUrl() {
        try {
            // Lấy đường dẫn tuyệt đối của database
            // Use user.dir (current working directory) which is project root when running
            // via mvn/IDE
            String cwd = System.getProperty("user.dir");
            File dbFile = new File(cwd, "src/main/resources/database/ltnc.db");

            // Fallback if not found (e.g. running from target?)
            if (!dbFile.exists()) {
                // Try classpath? No, we need write access.
                // Maybe create it?
                // For now, stick to this but ensure logging.
                System.err.println("Database file not found at: " + dbFile.getAbsolutePath());
                throw new RuntimeException("Database file not found: " + dbFile.getAbsolutePath());
            }

            return "jdbc:sqlite:" + dbFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("Failed to locate database file", e);
        }
    }

    // =========================
    // GET CONNECTION
    // =========================
    public static Connection getConnection() throws SQLException {
        // Always return a new connection to avoid "closed connection" errors
        // when using try-with-resources across multiple DAO calls.
        return DriverManager.getConnection(getDatabaseUrl());
    }

    // =========================
    // INIT DATABASE
    // =========================
    public static void init() {
        try (Connection conn = getConnection()) {
            System.out.println("✓ Database initialized successfully");
            System.out.println("✓ Database location: " + getDatabaseUrl());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    // =========================
    // CLOSE CONNECTION
    // =========================
    public static void close() {
        // No-op: Connections are managed by try-with-resources in DAO
    }
}