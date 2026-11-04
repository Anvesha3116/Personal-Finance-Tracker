package com.financetracker.database;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opens SQLite connections (finance.db in the folder the app is started from). */
public final class DbConnection {
    public static final Path DB_PATH = Paths.get(System.getProperty("user.dir"), "finance.db");

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DbConnection() {}

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    /** Opens a connection; commits on success, rolls back on error, always closes. */
    public static <T> T execute(Work<T> work) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    public static void init() {
        execute(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            date TEXT NOT NULL,
                            amount REAL NOT NULL CHECK (amount > 0),
                            category TEXT NOT NULL,
                            type TEXT NOT NULL CHECK (type IN ('Income', 'Expense')),
                            description TEXT NOT NULL DEFAULT ''
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS receipts (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            transaction_id INTEGER NOT NULL REFERENCES transactions(id),
                            shop TEXT NOT NULL,
                            file_name TEXT NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS budgets (
                            category TEXT PRIMARY KEY,
                            monthly_limit REAL NOT NULL,
                            warning_threshold REAL NOT NULL DEFAULT 0.8
                        )""");
            }
            return null;
        });
    }

    /** Turns every row of a result set into a column-name to value map. */
    static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<Map<String, Object>> out = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) row.put(md.getColumnLabel(i), rs.getObject(i));
            out.add(row);
        }
        return out;
    }
}
