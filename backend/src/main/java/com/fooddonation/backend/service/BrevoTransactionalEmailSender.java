package com.fooddonation.backend.service;

import com.fooddonation.backend.exception.BrevoEmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class BrevoTransactionalEmailSender {

    private static final Logger logger = LoggerFactory.getLogger(BrevoTransactionalEmailSender.class);
    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String senderAddress;

    public BrevoTransactionalEmailSender(
            RestClient.Builder restClientBuilder,
            @Value("${BREVO_API_KEY:}") String apiKey,
            @Value("${app.mail.from}") String senderAddress) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.senderAddress = senderAddress;
    }

    public void sendResetEmail(String recipient, String subject, String textContent) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("BREVO_API_KEY is not configured; reset email was not sent.");
            throw new BrevoEmailDeliveryException();
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", "Annadaan!", "email", senderAddress),
                "to", List.of(Map.of("email", recipient)),
                "subject", subject,
                "textContent", textContent
        );

        try {
            restClient.post()
                    .uri(API_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            logger.error("Brevo rejected a password reset email with HTTP status {}.", e.getStatusCode().value());
            throw new BrevoEmailDeliveryException();
        } catch (RestClientException e) {
            logger.error("Brevo password reset request failed ({}).", e.getClass().getSimpleName());
            throw new BrevoEmailDeliveryException();
        }
    }
}