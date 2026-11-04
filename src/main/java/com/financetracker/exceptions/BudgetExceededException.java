package com.financetracker.exceptions;

import java.util.Locale;
import java.util.Map;

/** Raised when an expense pushes a category past its monthly budget (the expense is already saved). */
public class BudgetExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String category;
    private final double limit;
    private final double spent;
    private final transient Map<String, Object> transaction;

    public BudgetExceededException(String category, double limit, double spent, Map<String, Object> transaction) {
        super(String.format(Locale.US, "%s budget exceeded: spent ₹%,.2f of ₹%,.2f", category, spent, limit));
        this.category = category;
        this.limit = limit;
        this.spent = spent;
        this.transaction = transaction;
    }

    public String category() { return category; }
    public double limit() { return limit; }
    public double spent() { return spent; }
    public Map<String, Object> transaction() { return transaction; }
}
