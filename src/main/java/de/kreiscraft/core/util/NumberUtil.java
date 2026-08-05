package de.kreiscraft.core.util;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class NumberUtil {

    private NumberUtil() {}

    public static OptionalInt parseInt(String s) {
        if (s == null || s.isBlank())
            return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static OptionalDouble parseDouble(String s) {
        if (s == null || s.isBlank())
            return OptionalDouble.empty();
        try {
            return OptionalDouble.of(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
