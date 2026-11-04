package com.financetracker.services;

import com.financetracker.database.ReceiptRepository;
import com.financetracker.exceptions.BudgetExceededException;
import com.financetracker.exceptions.InvalidTransactionException;
import com.financetracker.models.Receipt;
import com.financetracker.util.Num;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saves a receipt as an expense plus its image, and analyses saved receipts. */
public class ReceiptService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final FinanceService finance;
    private final ReceiptRepository repo;
    private final Path directory = Paths.get(System.getProperty("user.dir"), "receipts");

    public ReceiptService(FinanceService finance, ReceiptRepository repo) {
        this.finance = finance;
        this.repo = repo;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path directory() {
        return directory;
    }

    /** Identifies the real image type from its first bytes, not from the file name. */
    static String imageType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == '\r' && d[5] == '\n' && d[6] == 0x1A && d[7] == '\n') return ".png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return ".jpg";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return ".webp";
        return null;
    }

    public Map<String, Object> addReceipt(Map<String, String> form, byte[] data) {
        List<String> errors = new ArrayList<>();
        String ext = imageType(data);
        if (data.length == 0) {
            errors.add("image: please choose a receipt image");
        } else if (data.length > MAX_BYTES) {
            errors.add("image: must be 5 MB or smaller");
        } else if (ext == null) {
            errors.add("image: must be a PNG, JPG or WebP picture");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + (ext != null ? ext : ".png");
        Receipt receipt = Receipt.of(form.getOrDefault("shop", ""), name, errors);
        if (!errors.isEmpty()) throw new InvalidTransactionException("Invalid receipt", errors);

        Map<String, Object> expense = new LinkedHashMap<>();
        expense.put("type", "Expense");
        expense.put("date", form.get("date"));
        expense.put("amount", form.get("amount"));
        expense.put("category", form.get("category"));
        expense.put("description", "Receipt: " + receipt.shop());

        Map<String, Object> saved;
        try {
            saved = finance.addTransaction(expense); // budget rules apply as usual
        } catch (BudgetExceededException e) {        // expense is saved, so keep its receipt too
            store(((Number) e.transaction().get("id")).longValue(), receipt, data);
            throw e;
        }
        store(((Number) saved.get("id")).longValue(), receipt, data);
        return saved;
    }

    private void store(long transactionId, Receipt receipt, byte[] data) {
        try {
            Files.write(directory.resolve(receipt.fileName()), data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        repo.insert(transactionId, receipt.shop(), receipt.fileName());
    }

    public void removeForTransaction(long transactionId) {
        for (String name : repo.fileNamesFor(transactionId)) {
            try {
                Files.deleteIfExists(directory.resolve(name));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        repo.deleteForTransaction(transactionId);
    }

    public Map<String, Object> overview(String month) {
        List<Map<String, Object>> rows = repo.fetch(month);
        double total = 0;
        for (Map<String, Object> r : rows) total += Num.toDouble(r.get("amount"));
        int count = rows.size();

        // Group by shop name, ignoring upper/lower case; keep the first spelling seen.
        Map<String, double[]> sums = new LinkedHashMap<>();   // key -> [total, count]
        Map<String, String> display = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String shop = (String) r.get("shop");
            String key = shop.toLowerCase();
            display.putIfAbsent(key, shop);
            double[] agg = sums.computeIfAbsent(key, k -> new double[2]);
            agg[0] += Num.toDouble(r.get("amount"));
            agg[1] += 1;
        }
        List<Map<String, Object>> byShop = new ArrayList<>();
        sums.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))   // stable sort
                .limit(5)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("shop", display.get(e.getKey()));
                    m.put("total", Num.round(e.getValue()[0], 2));
                    m.put("count", (int) e.getValue()[1]);
                    byShop.add(m);
                });

        List<Map<String, Object>> largest = new ArrayList<>();
        rows.stream()
                .sorted(Comparator.comparingDouble((Map<String, Object> r) -> Num.toDouble(r.get("amount"))).reversed())
                .limit(3)
                .forEach(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("shop", r.get("shop"));
                    m.put("amount", Num.toDouble(r.get("amount")));
                    m.put("date", r.get("date"));
                    largest.add(m);
                });

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("count", count);
        analysis.put("total", Num.round(total, 2));
        analysis.put("average", count > 0 ? Num.round(total / count, 2) : 0);
        analysis.put("by_shop", byShop);
        analysis.put("largest", largest);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows);
        out.put("analysis", analysis);
        return out;
    }
}
