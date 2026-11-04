package com.financetracker.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SQL for the receipts table (images themselves live in the receipts/ folder). */
public class ReceiptRepository {

    public ReceiptRepository() {
        DbConnection.init();
    }

    public void insert(long transactionId, String shop, String fileName) {
        DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO receipts (transaction_id, shop, file_name) VALUES (?, ?, ?)")) {
                ps.setLong(1, transactionId);
                ps.setString(2, shop);
                ps.setString(3, fileName);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** month may be null for all receipts. */
    public List<Map<String, Object>> fetch(String month) {
        String sql = "SELECT r.id, r.transaction_id, r.shop, r.file_name, t.date, t.amount, t.category "
                + "FROM receipts r JOIN transactions t ON t.id = r.transaction_id"
                + (month != null ? " WHERE substr(t.date, 1, 7) = ?" : "")
                + " ORDER BY t.date DESC, r.id DESC";
        return DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (month != null) ps.setString(1, month);
                try (ResultSet rs = ps.executeQuery()) {
                    return DbConnection.rows(rs);
                }
            }
        });
    }

    public List<String> fileNamesFor(long transactionId) {
        return DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT file_name FROM receipts WHERE transaction_id = ?")) {
                ps.setLong(1, transactionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> names = new ArrayList<>();
                    while (rs.next()) names.add(rs.getString(1));
                    return names;
                }
            }
        });
    }

    public void deleteForTransaction(long transactionId) {
        DbConnection.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM receipts WHERE transaction_id = ?")) {
                ps.setLong(1, transactionId);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
