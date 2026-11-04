package com.financetracker.database;

import com.financetracker.models.Budget;
import com.financetracker.models.Transaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Raw SQL CRUD. All queries are parameterized. */
public class TransactionRepository {

    public TransactionRepository() {
        DbConnection.init();
    }

    public long insert(Transaction t) {
        return DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (date, amount, category, type, description) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, t.date().toString());
                ps.setDouble(2, t.amount());
                ps.setString(3, t.category());
                ps.setString(4, t.type());
                ps.setString(5, t.description());
                ps.executeUpdate();
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    /** Any filter may be null. Newest first. */
    public List<Map<String, Object>> fetch(String month, String type, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (month != null) { sql.append(" AND substr(date, 1, 7) = ?"); args.add(month); }
        if (type != null) { sql.append(" AND type = ?"); args.add(type); }
        if (category != null) { sql.append(" AND category = ?"); args.add(category); }
        sql.append(" ORDER BY date DESC, id DESC");
        return DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) ps.setString(i + 1, args.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    return DbConnection.rows(rs);
                }
            }
        });
    }

    public boolean delete(long id) {
        return DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public void upsertBudget(Budget b) {
        DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO budgets (category, monthly_limit, warning_threshold) VALUES (?, ?, ?) "
                            + "ON CONFLICT(category) DO UPDATE SET monthly_limit = excluded.monthly_limit, "
                            + "warning_threshold = excluded.warning_threshold")) {
                ps.setString(1, b.category());
                ps.setDouble(2, b.monthlyLimit());
                ps.setDouble(3, b.warningThreshold());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<Map<String, Object>> fetchBudgets() {
        return DbConnection.execute(conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM budgets ORDER BY category")) {
                return DbConnection.rows(rs);
            }
        });
    }
}
