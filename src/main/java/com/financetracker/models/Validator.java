package com.financetracker.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Field-by-field validator. Collects every problem (like Pydantic did) as "field: message"
 * so the web page can show them all at once.
 */
public final class Validator {
    private final Map<String, ?> data;
    private final List<String> errors = new ArrayList<>();

    public Validator(Map<String, ?> data) {
        this.data = data;
    }

    public List<String> errors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public void error(String field, String message) { errors.add(field + ": " + message); }

    /** defaultValue == null means the field is required. max <= 0 means no upper limit. */
    public String string(String field, String defaultValue, boolean strip, int min, int max) {
        Object raw = data.get(field);
        if (raw == null) {
            if (defaultValue == null) { error(field, "Field required"); return null; }
            raw = defaultValue;
        }
        if (!(raw instanceof String)) { error(field, "Input should be a valid string"); return null; }
        String s = (String) raw;
        if (strip) s = s.strip();
        int length = s.codePointCount(0, s.length());
        if (length < min) {
            error(field, "String should have at least " + min + " character" + (min == 1 ? "" : "s"));
            return null;
        }
        if (max > 0 && length > max) {
            error(field, "String should have at most " + max + " characters");
            return null;
        }
        return s;
    }

    /** Number greater than gt and at most le. Accepts JSON numbers or numeric strings (form fields). */
    public Double number(String field, double gt, double le, Double defaultValue) {
        Object raw = data.get(field);
        double v;
        if (raw == null) {
            if (defaultValue == null) { error(field, "Field required"); return null; }
            return defaultValue;
        } else if (raw instanceof Number n) {
            v = n.doubleValue();
        } else if (raw instanceof String s) {
            try {
                v = new BigDecimal(s.strip()).doubleValue();
            } catch (NumberFormatException e) {
                error(field, "Input should be a valid number, unable to parse string as a number");
                return null;
            }
        } else {
            error(field, "Input should be a valid number");
            return null;
        }
        if (!(v > gt)) { error(field, "Input should be greater than " + fmt(gt)); return null; }
        if (v > le) { error(field, "Input should be less than or equal to " + fmt(le)); return null; }
        return v;
    }

    public LocalDate date(String field) {
        Object raw = data.get(field);
        if (raw == null) { error(field, "Field required"); return null; }
        if (raw instanceof String s) {
            try {
                return LocalDate.parse(s.strip());
            } catch (DateTimeParseException ignored) {
                // falls through to the error below
            }
        }
        error(field, "Input should be a valid date in the format YYYY-MM-DD");
        return null;
    }

    public String choice(String field, String first, String second) {
        Object raw = data.get(field);
        if (raw == null) { error(field, "Field required"); return null; }
        if (first.equals(raw) || second.equals(raw)) return (String) raw;
        error(field, "Input should be '" + first + "' or '" + second + "'");
        return null;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
