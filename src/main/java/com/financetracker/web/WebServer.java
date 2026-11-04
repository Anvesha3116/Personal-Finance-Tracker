package com.financetracker.web;

import com.financetracker.exceptions.BudgetExceededException;
import com.financetracker.exceptions.InvalidTransactionException;
import com.financetracker.services.AnalyticsService;
import com.financetracker.services.FinanceService;
import com.financetracker.services.ReceiptService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP server and the /api routes (the Java equivalent of the Flask app). */
public class WebServer {
    private static final int MAX_BODY = 6 * 1024 * 1024;
    private static final long DRAIN_LIMIT = 64L * 1024 * 1024;
    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final Pattern TX_ID = Pattern.compile("/api/transactions/(\\d+)");
    private static final Pattern RECEIPT_FILE = Pattern.compile("[0-9a-f]{32}\\.(png|jpg|webp)");
    private static final Pattern STATIC_FILE = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9]+)?");

    private static class TooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final FinanceService finance;
    private final AnalyticsService analytics;
    private final ReceiptService receipts;
    private HttpServer server;

    public WebServer(FinanceService finance, AnalyticsService analytics, ReceiptService receipts) {
        this.finance = finance;
        this.analytics = analytics;
        this.receipts = receipts;
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    // ---------------------------------------------------------------- routing

    private void handle(HttpExchange ex) throws IOException {
        try {
            route(ex);
        } catch (TooLargeException e) {
            sendJson(ex, 413, Map.of("error", "That file is too large (maximum 5 MB)."));
        } catch (InvalidTransactionException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            body.put("details", e.errors());
            sendJson(ex, 400, body);
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", "Internal server error"));
        } finally {
            ex.close();
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        boolean get = method.equals("GET"), post = method.equals("POST");

        if (get && path.equals("/")) { sendStatic(ex, "index.html"); return; }
        if (get && path.startsWith("/static/")) { sendStatic(ex, path.substring("/static/".length())); return; }

        if (get && path.equals("/api/meta")) { sendJson(ex, 200, finance.categories()); return; }

        if (get && path.equals("/api/transactions")) {
            String type = query.get("type");
            sendJson(ex, 200, finance.listTransactions(month(query), type == null || type.isEmpty() ? null : type));
            return;
        }
        if (post && path.equals("/api/transactions")) {
            Map<String, Object> data = Json.readObject(readBody(ex));
            try {
                sendJson(ex, 201, finance.addTransaction(data));
            } catch (BudgetExceededException e) {   // saved, but over budget
                sendJson(ex, 201, withAlert(e));
            }
            return;
        }
        Matcher tx = TX_ID.matcher(path);
        if (method.equals("DELETE") && tx.matches()) {
            deleteTransaction(ex, tx.group(1));
            return;
        }

        if (post && path.equals("/api/receipts")) { addReceipt(ex); return; }
        if (get && path.equals("/api/receipts")) { sendJson(ex, 200, receipts.overview(month(query))); return; }
        if (get && path.startsWith("/api/receipts/file/")) {
            sendReceiptFile(ex, path.substring("/api/receipts/file/".length()));
            return;
        }

        if (get && path.equals("/api/budgets")) { sendJson(ex, 200, finance.budgetStatus(month(query))); return; }
        if (post && path.equals("/api/budgets")) {
            sendJson(ex, 201, finance.setBudget(Json.readObject(readBody(ex))));
            return;
        }

        if (get && path.equals("/api/summary")) { sendJson(ex, 200, analytics.summary(month(query))); return; }
        if (get && path.equals("/api/chart.png")) {
            send(ex, 200, "image/png", analytics.chartPng(month(query)));
            return;
        }

        sendJson(ex, 404, Map.of("error", "Not found"));
    }

    private void deleteTransaction(HttpExchange ex, String idText) throws IOException {
        long id;
        try {
            id = Long.parseLong(idText);
        } catch (NumberFormatException e) {
            sendJson(ex, 404, Map.of("error", "Transaction not found"));
            return;
        }
        receipts.removeForTransaction(id);
        if (finance.deleteTransaction(id)) {
            ex.sendResponseHeaders(204, -1);
        } else {
            sendJson(ex, 404, Map.of("error", "Transaction not found"));
        }
    }

    private void addReceipt(HttpExchange ex) throws IOException {
        Multipart form = Multipart.parse(ex.getRequestHeaders().getFirst("Content-Type"), readBody(ex));
        byte[] image = form.files.getOrDefault("image", new byte[0]);
        try {
            sendJson(ex, 201, receipts.addReceipt(form.fields, image));
        } catch (BudgetExceededException e) {
            sendJson(ex, 201, withAlert(e));
        }
    }

    private void sendReceiptFile(HttpExchange ex, String name) throws IOException {
        Path file = receipts.directory().resolve(name);
        if (!RECEIPT_FILE.matcher(name).matches() || !Files.isRegularFile(file)) {
            sendJson(ex, 404, Map.of("error", "Not found"));
            return;
        }
        String type = name.endsWith(".png") ? "image/png" : name.endsWith(".jpg") ? "image/jpeg" : "image/webp";
        send(ex, 200, type, Files.readAllBytes(file));
    }

    private void sendStatic(HttpExchange ex, String name) throws IOException {
        byte[] data = null;
        if (STATIC_FILE.matcher(name).matches()) {
            try (InputStream in = WebServer.class.getResourceAsStream("/static/" + name)) {
                if (in != null) data = in.readAllBytes();
            }
        }
        if (data == null) {
            sendJson(ex, 404, Map.of("error", "Not found"));
            return;
        }
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "text/javascript; charset=utf-8"
                : "application/octet-stream";
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        send(ex, 200, type, data);
    }

    // ---------------------------------------------------------------- helpers

    /** The ?month=YYYY-MM argument, defaulting to the current month. */
    private static String month(Map<String, String> query) {
        String m = query.get("month");
        if (m == null || m.isEmpty()) m = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        if (!MONTH.matcher(m).matches()) throw new InvalidTransactionException("Month must look like 2026-10");
        return m;
    }

    private static Map<String, Object> withAlert(BudgetExceededException e) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("level", "exceeded");
        alert.put("category", e.category());
        alert.put("spent", e.spent());
        alert.put("limit", e.limit());
        Map<String, Object> body = new LinkedHashMap<>(e.transaction());
        body.put("alert", alert);
        return body;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.putIfAbsent(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return out;
    }

    /** Reads the request body, refusing anything over 6 MB. */
    private static byte[] readBody(HttpExchange ex) throws IOException {
        String length = ex.getRequestHeaders().getFirst("Content-Length");
        if (length != null) {
            try {
                if (Long.parseLong(length.trim()) > MAX_BODY) {
                    drain(ex.getRequestBody());
                    throw new TooLargeException();
                }
            } catch (NumberFormatException ignored) {
                // fall through to the bounded read below
            }
        }
        byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            drain(ex.getRequestBody());
            throw new TooLargeException();
        }
        return body;
    }

    /** Throws away the rest of an oversized upload so the browser can still read our error reply. */
    private static void drain(InputStream in) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while (total < DRAIN_LIMIT && (n = in.read(buffer)) >= 0) total += n;
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json", Json.write(body));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
