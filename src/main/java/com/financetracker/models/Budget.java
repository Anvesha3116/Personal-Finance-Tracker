package com.financetracker.models;

import com.financetracker.exceptions.InvalidTransactionException;
import com.financetracker.util.Num;

import java.util.LinkedHashMap;
import java.util.Map;

/** Monthly spending limit for one expense category, with a warning level. */
public final class Budget {
    private final String category;
    private final double monthlyLimit;
    private final double warningThreshold;

    private Budget(String category, double monthlyLimit, double warningThreshold) {
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.warningThreshold = warningThreshold;
    }

    public static Budget parse(Map<String, ?> data) {
        Validator v = new Validator(data);
        String category = v.string("category", null, false, 0, 0);
        if (category != null && !Transaction.EXPENSE_CATEGORIES.contains(category)) {
            v.error("category", "'" + category + "' is not an expense category");
        }
        Double limit = v.number("monthly_limit", 0, 10_000_000, null);
        Double threshold = v.number("warning_threshold", 0, 1, 0.8);
        if (v.hasErrors()) throw new InvalidTransactionException("Invalid budget", v.errors());
        return new Budget(category, limit, threshold);
    }

    /** Builds a budget from a database row (already validated when it was saved). */
    public static Budget fromRow(Map<String, Object> row) {
        return new Budget((String) row.get("category"), Num.toDouble(row.get("monthly_limit")),
                Num.toDouble(row.get("warning_threshold")));
    }

    public String category() { return category; }
    public double monthlyLimit() { return monthlyLimit; }
    public double warningThreshold() { return warningThreshold; }

    public String status(double spent) {
        if (spent > monthlyLimit) return "exceeded";
        return spent >= monthlyLimit * warningThreshold ? "warning" : "ok";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("monthly_limit", monthlyLimit);
        m.put("warning_threshold", warningThreshold);
        return m;
    }
}
