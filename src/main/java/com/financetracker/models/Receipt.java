package com.financetracker.models;

import java.util.List;
import java.util.Map;

/** A receipt image linked to one expense transaction. */
public record Receipt(String shop, String fileName) {

    /** Validates the shop name (trimmed, 1 to 80 characters). Problems are added to errors; returns null if any. */
    public static Receipt of(String shop, String fileName, List<String> errors) {
        Validator v = new Validator(Map.of("shop", shop == null ? "" : shop));
        String cleaned = v.string("shop", "", true, 1, 80);
        if (v.hasErrors()) {
            errors.addAll(v.errors());
            return null;
        }
        return new Receipt(cleaned, fileName);
    }
}
