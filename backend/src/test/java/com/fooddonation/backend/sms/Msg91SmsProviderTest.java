package com.fooddonation.backend.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddonation.backend.entity.SmsNotificationLog.DeliveryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Msg91SmsProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Unconfigured MSG91 provider reports unavailable and fails gracefully without network calls")
    void testUnconfiguredProvider() {
        RestClient.Builder builder = RestClient.builder();
        Msg91SmsProvider provider = new Msg91SmsProvider(builder, "", "", "", "", objectMapper);

        assertFalse(provider.isAvailable());
        assertEquals("MSG91", provider.getName());

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("919876543210")
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
        assertTrue(result.getFailureReason().contains("incomplete"));
    }

    @Test
    @DisplayName("MSG91 rejects invalid phone format before sending network request")
    void testInvalidPhoneRejection() {
        RestClient.Builder builder = RestClient.builder();
        Msg91SmsProvider provider = new Msg91SmsProvider(builder, "dummy-key", "dummy-tpl", "dummy-sender", "", objectMapper);

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
    @DisplayName("Configured MSG91 provider builds payload, sends request with authkey, and extracts message ID")
    void testSuccessfulMsg91Send() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        Msg91SmsProvider provider = new Msg91SmsProvider(
                builder.build(),
                "msg91-secret-key-12345",
                "template-flow-xyz",
                "ANNADN",
                "https://control.msg91.com/api/v5/flow/",
                objectMapper
        );

        server.expect(requestTo("https://control.msg91.com/api/v5/flow/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authkey", "msg91-secret-key-12345"))
                .andExpect(jsonPath("$.template_id").value("template-flow-xyz"))
                .andExpect(jsonPath("$.sender").value("ANNADN"))
                .andExpect(jsonPath("$.recipients[0].mobiles").value("919876543210"))
                .andExpect(jsonPath("$.recipients[0].food_name").value("Fresh Biryani"))
                .andExpect(jsonPath("$.recipients[0].distance_km").value("3.5"))
                .andRespond(withSuccess("{\"type\":\"success\",\"request_id\":\"msg91-req-998877\"}", MediaType.APPLICATION_JSON));

        SmsSendRequest request = SmsSendRequest.builder()
                .recipientPhone("9876543210")
                .donationId(42L)
                .foodName("Fresh Biryani")
                .quantity(15.0)
                .unit("MEALS")
                .distanceKm(3.5)
                .pickupDeadline(LocalDateTime.of(2026, 11, 10, 18, 30))
                .appLink("http://localhost:5173/ngo/dashboard")
                .templateVariables(Map.of("custom_var", "val"))
                .build();

        SmsSendResult result = provider.sendDonationAlert(request);

        server.verify();
        assertTrue(result.isSuccess());
        assertEquals(DeliveryStatus.SENT, result.getDeliveryStatus());
        assertEquals("msg91-req-998877", result.getProviderMessageId());
        assertEquals("MSG91", result.getProviderName());
    }

    @Test
    @DisplayName("MSG91 HTTP 400 error returns failure status gracefully (Requirement 10)")
    void testMsg91HttpError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        Msg91SmsProvider provider = new Msg91SmsProvider(
                builder.build(),
                "msg91-secret-key",
                "template-id",
                "ANNADN",
                "https://control.msg91.com/api/v5/flow/",
                objectMapper
        );

        server.expect(requestTo("https://control.msg91.com/api/v5/flow/"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"message\":\"Invalid sender ID\"}"));

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
    }
}
