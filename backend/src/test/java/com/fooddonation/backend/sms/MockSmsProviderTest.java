package com.fooddonation.backend.sms;

import com.fooddonation.backend.entity.SmsNotificationLog.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSmsProviderTest {

    private MockSmsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockSmsProvider();
    }

    @Test
    @DisplayName("Mock provider returns name 'MOCK'")
    void testProviderName() {
        assertEquals("MOCK", provider.getName());
    }

    @Test
    @DisplayName("Mock provider simulates SMS dispatch without network calls and returns SENT status (Requirement 17)")
    void testMockSendSuccess() {
        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("919876543210")
                .foodName("Hot Meals")
                .quantity(25.0)
                .unit("MEALS")
                .distanceKm(2.4)
                .appLink("http://localhost:5173/ngo/dashboard")
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        // Requirement 18: Never mark DELIVERED upon dispatch
        assertEquals(DeliveryStatus.SENT, result.getDeliveryStatus());
        assertNotEquals(DeliveryStatus.DELIVERED, result.getDeliveryStatus());
        assertNotNull(result.getProviderMessageId());
        assertTrue(result.getProviderMessageId().startsWith("mock-"));
    }
}
