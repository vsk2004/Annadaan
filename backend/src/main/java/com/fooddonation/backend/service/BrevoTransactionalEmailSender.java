package com.fooddonation.backend.service;

import com.fooddonation.backend.exception.BrevoEmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrevoTransactionalEmailSender {

    private static final Logger logger = LoggerFactory.getLogger(BrevoTransactionalEmailSender.class);
    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String senderAddress;
    private final String senderName;

    @Autowired
    public BrevoTransactionalEmailSender(
            RestClient.Builder restClientBuilder,
            @Value("${BREVO_API_KEY:${brevo.api-key:}}") String apiKey,
            @Value("${app.mail.from:projectannadaan@gmail.com}") String senderAddress,
            @Value("${app.mail.from-name:${MAIL_FROM_NAME:Annadaan}}") String senderName) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey != null ? apiKey.trim() : null;
        this.senderAddress = senderAddress != null ? senderAddress.trim() : "projectannadaan@gmail.com";
        this.senderName = (senderName != null && !senderName.isBlank()) ? senderName.trim() : "Annadaan";
    }

    // Overloaded constructor for backwards compatibility with tests and callers
    public BrevoTransactionalEmailSender(
            RestClient.Builder restClientBuilder,
            String apiKey,
            String senderAddress) {
        this(restClientBuilder, apiKey, senderAddress, "Annadaan");
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String sendEmail(String recipientEmail, String recipientName, String subject,
                            String htmlContent, String textContent) {
        if (!isConfigured()) {
            logger.error("BREVO_API_KEY is not configured; email was not sent. Please set your Brevo API key in backend/.env.");
            throw new BrevoEmailDeliveryException("BREVO_API_KEY is not configured; email was not sent. Please set your Brevo API key in backend/.env.");
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            logger.error("Cannot send email: recipient email address is missing or blank");
            throw new BrevoEmailDeliveryException("Recipient email address is missing or blank");
        }

        Map<String, Object> payload = new LinkedHashMap<>();

        Map<String, Object> senderMap = new HashMap<>();
        senderMap.put("name", senderName);
        senderMap.put("email", senderAddress);
        payload.put("sender", senderMap);

        Map<String, Object> recipientMap = new HashMap<>();
        recipientMap.put("email", recipientEmail.trim());
        if (recipientName != null && !recipientName.isBlank()) {
            recipientMap.put("name", recipientName.trim());
        }
        payload.put("to", List.of(recipientMap));
        payload.put("subject", subject);

        if (htmlContent != null && !htmlContent.isBlank()) {
            payload.put("htmlContent", htmlContent);
        }
        if (textContent != null && !textContent.isBlank()) {
            payload.put("textContent", textContent);
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri(API_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            String messageId = response != null && response.get("messageId") != null
                    ? response.get("messageId").toString()
                    : null;

            logger.info("Email [{}] sent successfully via Brevo to {} (messageId: {})",
                    subject, recipientEmail, messageId);
            return messageId;
        } catch (RestClientResponseException e) {
            logger.error("Brevo rejected email [{}] with HTTP status {}. Response body: {}",
                    subject, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BrevoEmailDeliveryException("Brevo API error (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            logger.error("Brevo request failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BrevoEmailDeliveryException("Brevo connection failed: " + e.getMessage(), e);
        }
    }

    public void sendResetEmail(String recipient, String subject, String textContent) {
        sendEmail(recipient, null, subject, null, textContent);
    }
}
