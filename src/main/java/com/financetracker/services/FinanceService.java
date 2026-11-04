package com.financetracker.services;

import com.financetracker.database.TransactionRepository;
import com.financetracker.exceptions.BudgetExceededException;
import com.financetracker.models.Budget;
import com.financetracker.models.Transaction;
import com.financetracker.util.Num;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transaction processing, validation and budget rule enforcement. */
public class FinanceService {
    private final TransactionRepository repo;

    public FinanceService(TransactionRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> categories() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expense", Transaction.EXPENSE_CATEGORIES);
        m.put("income", Transaction.INCOME_CATEGORIES);
        return m;
    }

    /** Saves the transaction. Throws BudgetExceededException (carrying the saved row) if it breaks a budget. */
    public Map<String, Object> addTransaction(Map<String, ?> data) {
        Transaction t = Transaction.parse(data);
        t.setId(repo.insert(t));
        Map<String, Object> saved = t.toMap();
        if (t.type().equals("Expense")) {
            Map<String, Object> alert = checkBudget(t, saved);
            if (alert != null) saved.put("alert", alert);
        }
        return saved;
    }

    public List<Map<String, Object>> listTransactions(String month, String type) {
        return repo.fetch(month, type, null);
    }

    public boolean deleteTransaction(long id) {
        return repo.delete(id);
    }

    public Map<String, Object> setBudget(Map<String, ?> data) {
        Budget b = Budget.parse(data);
        repo.upsertBudget(b);
        return b.toMap();
    }

    public List<Map<String, Object>> budgetStatus(String month) {
        Map<String, Double> spent = new LinkedHashMap<>();
        for (Map<String, Object> r : repo.fetch(month, "Expense", null)) {
            spent.merge((String) r.get("category"), Num.toDouble(r.get("amount")), Double::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : repo.fetchBudgets()) {
            Budget b = Budget.fromRow(row);
            double s = Num.round(spent.getOrDefault(b.category(), 0.0), 2);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", b.category());
            item.put("limit", b.monthlyLimit());
            item.put("spent", s);
            item.put("pct", Num.round(s / b.monthlyLimit() * 100, 1));
            item.put("status", b.status(s));
            out.add(item);
        }
        return out;
    }

    /** Throws if over the limit; returns an alert if this expense first reaches the warning level. */
    private Map<String, Object> checkBudget(Transaction t, Map<String, Object> saved) {
        Map<String, Object> row = repo.fetchBudgets().stream()
                .filter(b -> t.category().equals(b.get("category")))
                .findFirst().orElse(null);
        if (row == null) return null;

        String month = t.date().toString().substring(0, 7);
        List<Double> amounts = new ArrayList<>();
        for (Map<String, Object> r : repo.fetch(month, "Expense", t.category())) {
            amounts.add(Num.toDouble(r.get("amount")));
        }
        double spent = Num.round(Num.sum(amounts), 2);
        Budget budget = Budget.fromRow(row);
        String status = budget.status(spent);

        if (status.equals("exceeded")) {
            throw new BudgetExceededException(t.category(), budget.monthlyLimit(), spent, saved);
        }
        boolean crossed = spent - t.amount() < budget.monthlyLimit() * budget.warningThreshold();
        if (status.equals("warning") && crossed) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("level", "warning");
            alert.put("category", t.category());
            alert.put("spent", spent);
            alert.put("limit", budget.monthlyLimit());
            return alert;
        }
        return null;
    }
}
