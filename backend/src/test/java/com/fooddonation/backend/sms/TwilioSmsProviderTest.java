package com.fooddonation.backend.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddonation.backend.entity.SmsNotificationLog.DeliveryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioSmsProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Unconfigured Twilio provider reports unavailable and fails gracefully without network calls")
    void testUnconfiguredProvider() {
        RestClient.Builder builder = RestClient.builder();
        TwilioSmsProvider provider = new TwilioSmsProvider(builder, "", "", "", "", "", objectMapper);

        assertFalse(provider.isAvailable());
        assertEquals("TWILIO", provider.getName());

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("9876543210")
                .donationId(1L)
                .foodName("Rice")
                .quantity(10.0)
                .unit("KG")
                .distanceKm(3.0)
                .pickupDeadline(LocalDateTime.now().plusHours(2))
                .appLink("http://localhost:5173/ngo/dashboard")
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);
        assertFalse(result.isSuccess());
        assertEquals(DeliveryStatus.FAILED, result.getDeliveryStatus());
        assertEquals("TWILIO", result.getProviderName());
        assertTrue(result.getFailureReason().contains("incomplete"));
    }

    @Test
    @DisplayName("Twilio provider rejects invalid phone format before sending network request")
    void testInvalidPhoneRejection() {
        RestClient.Builder builder = RestClient.builder();
        TwilioSmsProvider provider = new TwilioSmsProvider(
                builder,
                "AC_dummy_account_sid_12345",
                "dummy_auth_token_secret",
                "+12025550123",
                "",
                "",
                objectMapper
        );

        assertTrue(provider.isAvailable());

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("12345") // invalid
                .donationId(1L)
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);
        assertFalse(result.isSuccess());
        assertEquals(DeliveryStatus.FAILED, result.getDeliveryStatus());
        assertTrue(result.getFailureReason().contains("Invalid mobile"));
    }

    @Test
    @DisplayName("Configured Twilio provider builds form payload, sends request with Basic Auth, and extracts message SID")
    void testSuccessfulTwilioSendWithFromNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String accountSid = "ACtest1234567890abcdef1234567890ab";
        String authToken = "authsecrettoken123456";
        String fromNumber = "+12025550123";
        String expectedUrl = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        String expectedBasicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        TwilioSmsProvider provider = new TwilioSmsProvider(
                builder.build(),
                accountSid,
                authToken,
                fromNumber,
                "",
                expectedUrl,
                objectMapper
        );

        server.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicAuth))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("To=%2B919876543210")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("From=%2B12025550123")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Annadaan+Alert")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"sid\":\"SM998877665544332211\",\"status\":\"queued\"}"));

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("9876543210")
                .donationId(42L)
                .foodName("Fresh Biryani")
                .quantity(15.0)
                .unit("MEALS")
                .distanceKm(3.5)
                .pickupDeadline(LocalDateTime.of(2026, 11, 10, 18, 30))
                .appLink("http://localhost:5173/ngo/dashboard")
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);

        server.verify();
        assertTrue(result.isSuccess());
        assertEquals(DeliveryStatus.SENT, result.getDeliveryStatus());
        assertEquals("SM998877665544332211", result.getProviderMessageId());
        assertEquals("TWILIO", result.getProviderName());
    }

    @Test
    @DisplayName("Twilio provider successfully sends SMS using MessagingServiceSid instead of fromNumber")
    void testSuccessfulTwilioSendWithMessagingServiceSid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String accountSid = "ACtest1234567890abcdef1234567890ab";
        String authToken = "authsecrettoken123456";
        String serviceSid = "MG1234567890abcdef1234567890abcdef";
        String expectedUrl = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        TwilioSmsProvider provider = new TwilioSmsProvider(
                builder.build(),
                accountSid,
                authToken,
                "", // No fromNumber
                serviceSid, // MessagingServiceSid provided
                expectedUrl,
                objectMapper
        );

        assertTrue(provider.isAvailable());

        server.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MessagingServiceSid=" + serviceSid)))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"sid\":\"SMsvc12345\",\"status\":\"queued\"}"));

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("+919876543210")
                .donationId(10L)
                .foodName("Bread")
                .quantity(5.0)
                .unit("KG")
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);

        server.verify();
        assertTrue(result.isSuccess());
        assertEquals("SMsvc12345", result.getProviderMessageId());
    }

    @Test
    @DisplayName("Twilio HTTP 400 error returns failure status gracefully with parsed error message")
    void testTwilioHttpError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String accountSid = "ACtest1234567890";
        String expectedUrl = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        TwilioSmsProvider provider = new TwilioSmsProvider(
                builder.build(),
                accountSid,
                "token",
                "+12025550123",
                "",
                expectedUrl,
                objectMapper
        );

        server.expect(requestTo(expectedUrl))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":21606,\"message\":\"The From phone number is not a valid SMS-capable number.\",\"status\":400}"));

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("9876543210")
                .donationId(1L)
                .foodName("Food")
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);

        server.verify();
        assertFalse(result.isSuccess());
        assertEquals(DeliveryStatus.FAILED, result.getDeliveryStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("The From phone number is not a valid"));
    }

    @Test
    @DisplayName("Custom message in template variables overrides default message formatting")
    void testCustomMessageBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String accountSid = "ACtest1234567890";
        String expectedUrl = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        TwilioSmsProvider provider = new TwilioSmsProvider(
                builder.build(),
                accountSid,
                "token",
                "+12025550123",
                "",
                expectedUrl,
                objectMapper
        );

        server.expect(requestTo(expectedUrl))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Body=Custom+urgent+alert%21")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"sid\":\"SMcustom123\",\"status\":\"queued\"}"));

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("9876543210")
                .donationId(1L)
                .foodName("Ignored Food")
                .templateVariables(Map.of("message", "Custom urgent alert!"))
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);

        server.verify();
        assertTrue(result.isSuccess());
        assertEquals("SMcustom123", result.getProviderMessageId());
    }
}
