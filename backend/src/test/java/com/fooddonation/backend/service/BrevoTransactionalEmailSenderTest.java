package com.fooddonation.backend.service;

import com.fooddonation.backend.exception.BrevoEmailDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoTransactionalEmailSenderTest {

    private static final String API_KEY = "test-brevo-key";
    private static final String RESET_TOKEN = "test-reset-token";
    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

    private MockRestServiceServer server;
    private BrevoTransactionalEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new BrevoTransactionalEmailSender(builder, API_KEY, "verified@example.com", "Annadaan");
    }

    @Test
    void sendsBrevoTransactionalEmailOverHttps() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", API_KEY))
                .andExpect(jsonPath("$.sender.name").value("Annadaan"))
                .andExpect(jsonPath("$.sender.email").value("verified@example.com"))
                .andExpect(jsonPath("$.to[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString(RESET_TOKEN)))
                .andRespond(withSuccess("{\"messageId\":\"accepted\"}", MediaType.APPLICATION_JSON));

        sender.sendResetEmail("user@example.com", "Reset password", "Use token " + RESET_TOKEN);

        server.verify();
    }

    @Test
    void sendsHtmlAndTextEmailWithRecipientName() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", API_KEY))
                .andExpect(jsonPath("$.sender.name").value("Annadaan"))
                .andExpect(jsonPath("$.sender.email").value("verified@example.com"))
                .andExpect(jsonPath("$.to[0].name").value("John Donor"))
                .andExpect(jsonPath("$.to[0].email").value("john@example.com"))
                .andExpect(jsonPath("$.subject").value("Your Annadaan Donation Has Been Accepted!"))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("<h1>Accepted</h1>")))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("Accepted text")))
                .andRespond(withSuccess("{\"messageId\":\"brevo-msg-999\"}", MediaType.APPLICATION_JSON));

        String messageId = sender.sendEmail("john@example.com", "John Donor",
                "Your Annadaan Donation Has Been Accepted!", "<h1>Accepted</h1>", "Accepted text");

        assertEquals("brevo-msg-999", messageId);
        server.verify();
    }

    @Test
    void rejectsBrevoProviderFailureWithoutExposingSecrets() {
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider error"));

        BrevoEmailDeliveryException exception = assertThrows(BrevoEmailDeliveryException.class,
                () -> sender.sendResetEmail("user@example.com", "Reset password", RESET_TOKEN));

        assertFalse(exception.getMessage().contains(API_KEY));
        assertFalse(exception.getMessage().contains(RESET_TOKEN));
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyWithoutCallingProvider() {
        BrevoTransactionalEmailSender unconfiguredSender =
                new BrevoTransactionalEmailSender(RestClient.builder(), "", "verified@example.com");

        assertThrows(BrevoEmailDeliveryException.class,
                () -> unconfiguredSender.sendResetEmail("user@example.com", "Reset password", RESET_TOKEN));
        server.verify();
    }

    @Test
    void rejectsBlankRecipientEmail() {
        assertThrows(BrevoEmailDeliveryException.class,
                () -> sender.sendEmail("   ", "Donor", "Subject", "<p>hi</p>", "hi"));
    }
}
