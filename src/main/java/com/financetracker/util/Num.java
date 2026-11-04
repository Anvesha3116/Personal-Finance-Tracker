package com.financetracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public final class Num {
    private Num() {}

    /** Same result as Python's round(value, places): exact binary value, ties to even. */
    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return value;
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static double toDouble(Object o) {
        return ((Number) o).doubleValue();
    }

    /** Plain left-to-right sum (like Python's sum()). */
    public static double sum(Collection<Double> values) {
        double total = 0;
        for (double v : values) total += v;
        return total;
    }
}
