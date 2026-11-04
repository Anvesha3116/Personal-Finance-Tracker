package com.financetracker;

import com.financetracker.database.ReceiptRepository;
import com.financetracker.database.TransactionRepository;
import com.financetracker.services.AnalyticsService;
import com.financetracker.services.FinanceService;
import com.financetracker.services.ReceiptService;
import com.financetracker.web.WebServer;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Starts the Personal Finance Tracker on http://127.0.0.1:5000
 * Options: --port=5001   --no-browser      (or set the PORT environment variable)
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");   // charts are drawn off-screen

        int port = 5000;
        boolean openBrowser = true;
        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) port = Integer.parseInt(env.trim());
        for (String arg : args) {
            if (arg.equals("--no-browser")) openBrowser = false;
            else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring("--port=".length()));
        }

        TransactionRepository transactions = new TransactionRepository();
        ReceiptRepository receiptRepo = new ReceiptRepository();
        FinanceService finance = new FinanceService(transactions);
        AnalyticsService analytics = new AnalyticsService(transactions);
        ReceiptService receipts = new ReceiptService(finance, receiptRepo);

        new WebServer(finance, analytics, receipts).start(port);
        String url = "http://127.0.0.1:" + port;
        System.out.println("Personal Finance Tracker running at " + url + "  (Ctrl+C to stop)");
        if (openBrowser) scheduleBrowser(url);
    }

    private static void scheduleBrowser(String url) {
        new Timer(true).schedule(new TimerTask() {
            @Override public void run() {
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                    else if (os.contains("mac")) new ProcessBuilder("open", url).start();
                    else new ProcessBuilder("xdg-open", url).start();
                } catch (IOException ignored) {
                    // no browser available: the URL is printed above
                }
            }
        }, 1200);
    }
}
