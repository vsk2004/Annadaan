package com.fooddonation.backend.util;

import java.util.regex.Pattern;

public final class PhoneUtils {

    private static final Pattern INDIAN_10_DIGIT = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern INDIAN_12_DIGIT_WITH_91 = Pattern.compile("^91[6-9]\\d{9}$");
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private PhoneUtils() {}

    /**
     * Normalizes an Indian phone number to 12-digit format: 91XXXXXXXXXX (without +).
     * Returns null if the phone number is invalid.
     */
    public static String normalizeForMsg91(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }

        String digitsOnly = rawPhone.replaceAll("[^0-9]", "");

        if (INDIAN_12_DIGIT_WITH_91.matcher(digitsOnly).matches()) {
            return digitsOnly;
        }

        if (digitsOnly.length() == 11 && digitsOnly.startsWith("0")) {
            String sub = digitsOnly.substring(1);
            if (INDIAN_10_DIGIT.matcher(sub).matches()) {
                return "91" + sub;
            }
        }

        if (INDIAN_10_DIGIT.matcher(digitsOnly).matches()) {
            return "91" + digitsOnly;
        }

        return null;
    }

    /**
     * Alias for Indian mobile normalization to 12-digit format.
     */
    public static String normalizeIndianMobile(String rawPhone) {
        return normalizeForMsg91(rawPhone);
    }

    /**
     * Normalizes a phone number to standard E.164 format (e.g. +91XXXXXXXXXX) required by Twilio.
     * Handles 10-digit Indian numbers, 11-digit with leading 0, 12-digit with 91,
     * numbers already prefixed with +, and standard international numbers.
     * Returns null if the phone number is invalid.
     */
    public static String normalizeToE164(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }

        String trimmed = rawPhone.trim();
        // If already in international format starting with +
        if (trimmed.startsWith("+")) {
            String digits = trimmed.substring(1).replaceAll("[^0-9]", "");
            String candidate = "+" + digits;
            if (E164_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }

        // Try Indian mobile normalization
        String indianDigits = normalizeForMsg91(rawPhone);
        if (indianDigits != null) {
            return "+" + indianDigits;
        }

        return null;
    }

    /**
     * Normalizes a phone number for Twilio (alias for normalizeToE164).
     */
    public static String normalizeForTwilio(String rawPhone) {
        return normalizeToE164(rawPhone);
    }

    /**
     * Validates if a raw phone number is a valid Indian mobile number.
     */
    public static boolean isValidIndianMobile(String rawPhone) {
        return normalizeForMsg91(rawPhone) != null;
    }

    /**
     * Masks a phone number for privacy in application logs (e.g. 9198*****210).
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "[EMPTY]";
        }
        String clean = phone.replaceAll("\\s+", "");
        if (clean.length() <= 4) {
            return "****";
        }
        int visibleStart = Math.min(4, clean.length() / 2);
        int visibleEnd = Math.min(3, clean.length() / 3);
        int maskedLength = clean.length() - visibleStart - visibleEnd;
        if (maskedLength <= 0) {
            return clean.substring(0, 2) + "****";
        }
        return clean.substring(0, visibleStart) + "*".repeat(maskedLength) + clean.substring(clean.length() - visibleEnd);
    }
}
