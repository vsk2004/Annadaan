package com.fooddonation.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "9876543210",
            "+919876543210",
            "919876543210",
            "+91 98765 43210",
            "91-9876543210",
            "8123456789",
            "7012345678",
            "6301234567"
    })
    @DisplayName("Valid Indian mobile formats are accepted and normalized to 91XXXXXXXXXX")
    void testValidIndianMobile(String input) {
        assertTrue(PhoneUtils.isValidIndianMobile(input), "Expected valid Indian mobile for: " + input);
        String normalized = PhoneUtils.normalizeIndianMobile(input);
        assertNotNull(normalized);
        assertTrue(normalized.startsWith("91"));
        assertEquals(12, normalized.length());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9876543210",
            "+919876543210",
            "919876543210",
            "+91 98765 43210",
            "91-9876543210"
    })
    @DisplayName("Indian mobile numbers normalize to E.164 format (+91XXXXXXXXXX) for Twilio")
    void testTwilioE164Indian(String input) {
        String e164 = PhoneUtils.normalizeForTwilio(input);
        assertNotNull(e164);
        assertTrue(e164.startsWith("+91"));
        assertEquals(13, e164.length());
    }

    @Test
    @DisplayName("International numbers in E.164 format are preserved for Twilio")
    void testTwilioE164International() {
        assertEquals("+14155552671", PhoneUtils.normalizeForTwilio("+14155552671"));
        assertEquals("+447911123456", PhoneUtils.normalizeForTwilio("+44 7911 123456"));
        assertNull(PhoneUtils.normalizeForTwilio("12345"));
        assertNull(PhoneUtils.normalizeForTwilio(null));
        assertNull(PhoneUtils.normalizeForTwilio("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890", // starts with 1
            "0987654321", // 10 digits starting with 0
            "5876543210", // starts with 5
            "98765",      // too short
            "9876543210123", // too long
            "abcdefghij", // alphabetic
            "+14155552671" // US number
    })
    @DisplayName("Invalid mobile numbers are rejected")
    void testInvalidMobile(String input) {
        assertFalse(PhoneUtils.isValidIndianMobile(input), "Expected invalid mobile for: " + input);
        assertNull(PhoneUtils.normalizeIndianMobile(input));
    }

    @Test
    @DisplayName("Null or blank phone returns false and null normalization")
    void testNullOrBlankPhone() {
        assertFalse(PhoneUtils.isValidIndianMobile(null));
        assertFalse(PhoneUtils.isValidIndianMobile("   "));
        assertNull(PhoneUtils.normalizeIndianMobile(null));
        assertNull(PhoneUtils.normalizeIndianMobile("   "));
    }

    @Test
    @DisplayName("Phone masking masks middle digits and retains security")
    void testPhoneMasking() {
        String masked = PhoneUtils.maskPhone("919876543210");
        assertTrue(masked.startsWith("91"));
        assertTrue(masked.endsWith("210"));
        assertTrue(masked.contains("*"));
        assertFalse(masked.contains("987654"));

        assertEquals("[EMPTY]", PhoneUtils.maskPhone(null));
        assertEquals("****", PhoneUtils.maskPhone("123"));
    }
}
