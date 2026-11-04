package com.financetracker.models;

import com.financetracker.exceptions.InvalidTransactionException;
import com.financetracker.util.Num;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A single income or expense entry, amounts in ₹. */
public final class Transaction {
    public static final List<String> EXPENSE_CATEGORIES = List.of("Food & Groceries", "Rent", "Utilities",
            "Transport", "Entertainment", "Healthcare", "Education", "Shopping", "Other");
    public static final List<String> INCOME_CATEGORIES = List.of("Salary", "Freelance", "Pocket Money", "Other Income");

    private Long id;
    private final LocalDate date;
    private final double amount;
    private final String category;
    private final String type;
    private final String description;

    private Transaction(LocalDate date, double amount, String category, String type, String description) {
        this.date = date;
        this.amount = amount;
        this.category = category;
        this.type = type;
        this.description = description;
    }

    /** Validates raw input; throws InvalidTransactionException listing every problem found. */
    public static Transaction parse(Map<String, ?> data) {
        Validator v = new Validator(data);
        LocalDate date = v.date("date");
        if (date != null && date.isAfter(LocalDate.now())) {
            v.error("date", "date cannot be in the future");
        }
        Double amount = v.number("amount", 0, 10_000_000, null);
        String category = v.string("category", null, false, 0, 0);
        String type = v.choice("type", "Income", "Expense");
        String description = v.string("description", "", false, 0, 200);

        if (!v.hasErrors()) {
            List<String> allowed = type.equals("Expense") ? EXPENSE_CATEGORIES : INCOME_CATEGORIES;
            if (!allowed.contains(category)) {
                v.error("transaction", "'" + category + "' is not a valid " + type.toLowerCase() + " category");
            }
        }
        if (v.hasErrors()) throw new InvalidTransactionException("Invalid transaction", v.errors());
        return new Transaction(date, Num.round(amount, 2), category, type, description.strip());
    }

    public Long id() { return id; }
    public void setId(long id) { this.id = id; }
    public LocalDate date() { return date; }
    public double amount() { return amount; }
    public String category() { return category; }
    public String type() { return type; }
    public String description() { return description; }

    /** JSON shape sent to the browser. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("date", date.toString());
        m.put("amount", amount);
        m.put("category", category);
        m.put("type", type);
        m.put("description", description);
        return m;
    }
}
