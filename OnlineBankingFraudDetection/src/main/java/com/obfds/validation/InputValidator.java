package com.obfds.validation;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Single place for input rules. The console, the HTTP API and the Streamlit UI all rely on these
 * checks, so the same rules apply wherever a value enters the system.
 */
public final class InputValidator {
    private static final Pattern AMOUNT = Pattern.compile("\\d+(\\.\\d{1,2})?");
    private static final Pattern NATIONAL_ID = Pattern.compile("\\d{5}");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("[A-Z0-9-]{6,20}");
    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

    private InputValidator() {
    }

    /** Names: 2 to 60 characters, letters, spaces, dots, apostrophes and hyphens only. No numbers. */
    public static String name(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter the customer's full name");
        }
        if (value.length() < 2 || value.length() > 60) {
            throw new IllegalArgumentException("Customer name must be 2 to 60 characters");
        }
        for (char c : value.toCharArray()) {
            boolean allowed = Character.isLetter(c) || c == ' ' || c == '.' || c == '\'' || c == '-';
            if (!allowed) {
                throw new IllegalArgumentException(
                        "Customer name can only contain letters, spaces, dots, apostrophes and hyphens (no numbers)");
            }
        }
        return value;
    }

    /** CNIC: exactly 5 digits. */
    public static String nationalId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!NATIONAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("CNIC must be exactly 5 digits");
        }
        return value;
    }

    /** Amount that must be greater than zero. Digits only, with an optional decimal part. */
    public static double positiveAmount(String raw, String label) {
        double value = parseAmount(raw, label);
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return value;
    }

    /** Opening balance: digits only, zero allowed. */
    public static double openingAmount(String raw) {
        return parseAmount(raw, "Opening balance");
    }

    private static double parseAmount(String raw, String label) {
        String value = raw == null ? "" : raw.trim().replace(",", "").replace(" ", "");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter the " + label.toLowerCase(Locale.ROOT));
        }
        if (!AMOUNT.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must contain numbers only, for example 650000");
        }
        return Double.parseDouble(value);
    }

    /** Country must be a real ISO 3166 alpha-2 code, for example PK, AE or US. */
    public static String countryCode(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter a country code, for example PK");
        }
        if (!COUNTRY_CODES.contains(value)) {
            throw new IllegalArgumentException("Country code '" + value
                    + "' does not exist. Use a 2-letter ISO code such as PK, AE or US");
        }
        return value;
    }

    /** English country name for a valid code, for example AE -> United Arab Emirates. */
    public static String countryName(String code) {
        return new Locale("", code).getDisplayCountry(Locale.ENGLISH);
    }

    /** Account number (own or outside): 6 to 20 letters or digits, case-insensitive. */
    public static String accountNumber(String raw) {
        String value = raw == null ? "" : raw.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        if (!ACCOUNT_NUMBER.matcher(value).matches()) {
            throw new IllegalArgumentException("Recipient account number must be 6 to 20 letters or digits");
        }
        return value;
    }
}
