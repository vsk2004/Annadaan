package com.fooddonation.backend.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock private MockSmsProvider mockSmsProvider;
    @Mock private TwilioSmsProvider twilioSmsProvider;
    @Mock private Msg91SmsProvider msg91SmsProvider;

    private SmsSendRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = SmsSendRequest.builder()
                .donationId(1L)
                .recipientPhone("+919876543210")
                .foodName("Biryani")
                .build();
    }

    @Test
    @DisplayName("When SMS is disabled, SmsService routes to MockSmsProvider regardless of configured provider")
    void testSmsDisabledRoutesToMock() {
        SmsService service = new SmsService(false, "twilio", mockSmsProvider, twilioSmsProvider, msg91SmsProvider);
        assertFalse(service.isSmsEnabled());
        assertEquals("twilio", service.getConfiguredProvider());

        when(mockSmsProvider.sendDonationAlert(any())).thenReturn(SmsSendResult.success("mock-1", "MOCK"));

        SmsSendResult result = service.sendDonationAlert(sampleRequest);

        assertTrue(result.isSuccess());
        assertEquals("MOCK", result.getProviderName());
        verify(mockSmsProvider).sendDonationAlert(sampleRequest);
        verify(twilioSmsProvider, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("When SMS is enabled and provider is twilio, routes to TwilioSmsProvider if available")
    void testTwilioProviderSelectedWhenAvailable() {
        SmsService service = new SmsService(true, "twilio", mockSmsProvider, twilioSmsProvider, msg91SmsProvider);
        assertTrue(service.isSmsEnabled());

        when(twilioSmsProvider.isAvailable()).thenReturn(true);
        when(twilioSmsProvider.sendDonationAlert(any())).thenReturn(SmsSendResult.success("SM12345", "TWILIO"));

        SmsSendResult result = service.sendDonationAlert(sampleRequest);

        assertTrue(result.isSuccess());
        assertEquals("TWILIO", result.getProviderName());
        assertEquals("SM12345", result.getProviderMessageId());
        verify(twilioSmsProvider).sendDonationAlert(sampleRequest);
        verify(mockSmsProvider, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("When provider is twilio but unconfigured/unavailable, safely falls back to MockSmsProvider")
    void testTwilioFallbackToMockWhenUnavailable() {
        SmsService service = new SmsService(true, "twilio", mockSmsProvider, twilioSmsProvider, msg91SmsProvider);

        when(twilioSmsProvider.isAvailable()).thenReturn(false);
        when(mockSmsProvider.sendDonationAlert(any())).thenReturn(SmsSendResult.success("mock-fallback", "MOCK"));

        SmsSendResult result = service.sendDonationAlert(sampleRequest);

        assertTrue(result.isSuccess());
        assertEquals("MOCK", result.getProviderName());
        verify(mockSmsProvider).sendDonationAlert(sampleRequest);
        verify(twilioSmsProvider, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("When provider is mock, routes directly to MockSmsProvider")
    void testMockProviderExplicit() {
        SmsService service = new SmsService(true, "mock", mockSmsProvider, twilioSmsProvider, msg91SmsProvider);

        when(mockSmsProvider.sendDonationAlert(any())).thenReturn(SmsSendResult.success("mock-direct", "MOCK"));

        SmsSendResult result = service.sendDonationAlert(sampleRequest);

        assertTrue(result.isSuccess());
        assertEquals("MOCK", result.getProviderName());
        verify(mockSmsProvider).sendDonationAlert(sampleRequest);
        verify(twilioSmsProvider, never()).sendDonationAlert(any());
    }
}
