package com.financetracker.services;

import com.financetracker.database.TransactionRepository;
import com.financetracker.util.Num;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Monthly totals, category breakdown, six-month trend, and the donut chart (drawn with Java2D). */
public class AnalyticsService {
    private static final Color[] PALETTE = colors("#4D0E13", "#C8A49F", "#D8C4AC", "#7A2A31",
            "#A8736F", "#B49A7E", "#2D0709", "#E2CFC9");
    private static final Color INK = Color.decode("#4D0E13");
    private static final Color EDGE = Color.decode("#F7F1EA");

    private final TransactionRepository repo;

    public AnalyticsService(TransactionRepository repo) {
        this.repo = repo;
    }

    private static Color[] colors(String... hex) {
        Color[] c = new Color[hex.length];
        for (int i = 0; i < hex.length; i++) c[i] = Color.decode(hex[i]);
        return c;
    }

    /** Expense totals per category for the month, biggest first. */
    private Map<String, Double> breakdown(List<Map<String, Object>> rows, String month) {
        Map<String, Double> byCategory = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            if (month.equals(monthOf(r)) && "Expense".equals(r.get("type"))) {
                byCategory.merge((String) r.get("category"), Num.toDouble(r.get("amount")), Double::sum);
            }
        }
        Map<String, Double> sorted = new LinkedHashMap<>();
        byCategory.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private static String monthOf(Map<String, Object> row) {
        return ((String) row.get("date")).substring(0, 7);
    }

    public Map<String, Object> summary(String month) {
        List<Map<String, Object>> rows = repo.fetch(null, null, null);
        double income = 0, expense = 0;
        Map<String, double[]> perMonth = new LinkedHashMap<>();   // month -> [income, expense]
        for (Map<String, Object> r : rows) {
            double amount = Num.toDouble(r.get("amount"));
            boolean isIncome = "Income".equals(r.get("type"));
            double[] sums = perMonth.computeIfAbsent(monthOf(r), k -> new double[2]);
            sums[isIncome ? 0 : 1] += amount;
            if (month.equals(monthOf(r))) {
                if (isIncome) income += amount; else expense += amount;
            }
        }

        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Map.Entry<String, Double> e : breakdown(rows, month).entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", e.getKey());
            item.put("amount", Num.round(e.getValue(), 2));
            item.put("share", Num.round(e.getValue() / expense * 100, 1));
            breakdown.add(item);
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        YearMonth end = YearMonth.parse(month);
        for (int i = 5; i >= 0; i--) {
            YearMonth p = end.minusMonths(i);
            double[] sums = perMonth.getOrDefault(p.toString(), new double[2]);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", p.toString());
            item.put("label", p.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            item.put("income", sums[0]);
            item.put("expense", sums[1]);
            item.put("savings", Num.round(sums[0] - sums[1], 2));
            trend.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("income", Num.round(income, 2));
        out.put("expense", Num.round(expense, 2));
        out.put("savings", Num.round(income - expense, 2));
        out.put("savings_rate", income != 0 ? Num.round((income - expense) / income * 100, 1) : 0);
        out.put("breakdown", breakdown);
        out.put("trend", trend);
        return out;
    }

    /** PNG donut chart of the month's expenses by category, with a legend on the right. */
    public byte[] chartPng(String month) {
        Map<String, Double> spend = breakdown(repo.fetch(null, null, null), month);
        BufferedImage img = spend.isEmpty() ? emptyChart() : donut(spend);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Graphics2D prepare(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static BufferedImage emptyChart() {
        BufferedImage img = new BufferedImage(700, 120, BufferedImage.TYPE_INT_ARGB);   // transparent background
        Graphics2D g = prepare(img);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        g.setColor(INK);
        String text = "No expenses in this month";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (img.getWidth() - fm.stringWidth(text)) / 2,
                (img.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }

    private static BufferedImage donut(Map<String, Double> spend) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 22);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        FontMetrics fm = pg.getFontMetrics(font);

        List<String> labels = new ArrayList<>();
        double total = 0;
        int labelWidth = 0;
        for (Map.Entry<String, Double> e : spend.entrySet()) {
            String label = String.format(Locale.US, "%s  ₹%,d", e.getKey(), (long) Num.round(e.getValue(), 0));
            labels.add(label);
            labelWidth = Math.max(labelWidth, fm.stringWidth(label));
            total += e.getValue();
        }
        pg.dispose();

        int pad = 12, diameter = 420, gap = 36, swatchW = 30, swatchH = 16, swatchGap = 12;
        int rowH = fm.getHeight() + 10;
        int legendH = rowH * labels.size();
        int width = pad + diameter + gap + swatchW + swatchGap + labelWidth + pad;
        int height = Math.max(diameter, legendH) + 2 * pad;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(img);

        double cx = pad + diameter / 2.0, cy = height / 2.0;
        double outer = diameter / 2.0 - 3, inner = outer * 0.6;   // ring is 40% of the radius thick
        Area ring = new Area(new Ellipse2D.Double(cx - outer, cy - outer, 2 * outer, 2 * outer));
        ring.subtract(new Area(new Ellipse2D.Double(cx - inner, cy - inner, 2 * inner, 2 * inner)));

        double angle = 90;   // start at 12 o'clock and go clockwise
        int i = 0;
        for (double value : spend.values()) {
            double sweep = value / total * 360;
            Area wedge = new Area(new Arc2D.Double(cx - outer - 2, cy - outer - 2, 2 * outer + 4, 2 * outer + 4,
                    angle, -sweep, Arc2D.PIE));
            wedge.intersect(ring);
            g.setColor(PALETTE[i % PALETTE.length]);
            g.fill(wedge);
            g.setColor(EDGE);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(wedge);
            angle -= sweep;
            i++;
        }

        g.setFont(font);
        int x = pad + diameter + gap;
        int y = (height - legendH) / 2;
        for (int k = 0; k < labels.size(); k++) {
            int rowTop = y + k * rowH;
            g.setColor(PALETTE[k % PALETTE.length]);
            g.fillRect(x, rowTop + (rowH - swatchH) / 2, swatchW, swatchH);
            g.setColor(INK);
            g.drawString(labels.get(k), x + swatchW + swatchGap, rowTop + (rowH - fm.getHeight()) / 2 + fm.getAscent());
        }
        g.dispose();
        return img;
    }
}
