package com.fooddonation.backend.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddonation.backend.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Msg91SmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsProvider.class);
    public static final String PROVIDER_NAME = "MSG91";
    private static final String DEFAULT_API_URL = "https://control.msg91.com/api/v5/flow/";
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a, dd MMM");

    private final RestClient restClient;
    private final String authKey;
    private final String templateId;
    private final String senderId;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public Msg91SmsProvider(
            RestClient.Builder restClientBuilder,
            @Value("${msg91.auth-key:${MSG91_AUTH_KEY:}}") String authKey,
            @Value("${msg91.sms-template-id:${MSG91_SMS_TEMPLATE_ID:}}") String templateId,
            @Value("${msg91.sender-id:${MSG91_SENDER_ID:}}") String senderId,
            @Value("${msg91.api-url:" + DEFAULT_API_URL + "}") String apiUrl,
            ObjectMapper objectMapper) {
        this(buildRestClient(restClientBuilder), authKey, templateId, senderId, apiUrl, objectMapper);
    }

    public Msg91SmsProvider(
            RestClient restClient,
            String authKey,
            String templateId,
            String senderId,
            String apiUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.authKey = authKey != null ? authKey.trim() : "";
        this.templateId = templateId != null ? templateId.trim() : "";
        this.senderId = senderId != null ? senderId.trim() : "";
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl.trim() : DEFAULT_API_URL;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        return restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return !authKey.isBlank() && !templateId.isBlank() && !senderId.isBlank();
    }

    @Override
    public SmsSendResult sendDonationAlert(SmsSendRequest request) {
        if (!isAvailable()) {
            log.warn("MSG91 provider is not configured. Missing authKey, templateId, or senderId.");
            return SmsSendResult.failure("MSG91 configuration incomplete", PROVIDER_NAME);
        }

        String normalizedMobile = PhoneUtils.normalizeForMsg91(request.getRecipientPhone());
        if (normalizedMobile == null) {
            log.warn("Cannot send MSG91 SMS: invalid mobile format for recipient {}", request.getMaskedPhone());
            return SmsSendResult.failure("Invalid mobile phone number format", PROVIDER_NAME);
        }

        Map<String, Object> recipientData = new HashMap<>();
        recipientData.put("mobiles", normalizedMobile);

        if (request.getTemplateVariables() != null) {
            recipientData.putAll(request.getTemplateVariables());
        }

        recipientData.putIfAbsent("food_name", request.getFoodName() != null ? request.getFoodName() : "Food donation");
        recipientData.putIfAbsent("quantity", request.getQuantity() != null ? String.valueOf(request.getQuantity()) : "");
        recipientData.putIfAbsent("unit", request.getUnit() != null ? request.getUnit() : "MEALS");
        recipientData.putIfAbsent("distance_km", request.getDistanceKm() != null ? String.valueOf(request.getDistanceKm()) : "0");

        if (request.getPickupDeadline() != null) {
            recipientData.putIfAbsent("pickup_deadline", request.getPickupDeadline().format(DEADLINE_FORMATTER));
        }
        if (request.getAppLink() != null) {
            recipientData.putIfAbsent("app_link", request.getAppLink());
        }

        Map<String, Object> payload = Map.of(
                "template_id", templateId,
                "sender", senderId,
                "short_url", "0",
                "recipients", List.of(recipientData)
        );

        String masked = request.getMaskedPhone() != null
                ? request.getMaskedPhone()
                : PhoneUtils.maskPhone(normalizedMobile);

        try {
            String responseBody = restClient.post()
                    .uri(apiUrl)
                    .header("authkey", authKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            String providerMessageId = extractMessageId(responseBody);
            log.info("MSG91 SMS accepted for recipient {} (donation #{}, msgId: {})",
                    masked, request.getDonationId(), providerMessageId);

            return SmsSendResult.success(providerMessageId, PROVIDER_NAME);
        } catch (RestClientResponseException e) {
            log.error("MSG91 rejected SMS for {} with HTTP {}: {}",
                    masked, e.getStatusCode().value(), e.getResponseBodyAsString());
            return SmsSendResult.failure("MSG91 HTTP " + e.getStatusCode().value() + ": " + e.getStatusText(), PROVIDER_NAME);
        } catch (RestClientException e) {
            log.error("MSG91 request failed for {}: {}", masked, e.getMessage());
            return SmsSendResult.failure("MSG91 connection error: " + e.getMessage(), PROVIDER_NAME);
        }
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "msg91-unknown";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("request_id")) {
                return root.get("request_id").asText();
            }
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return "msg91-accepted";
    }
}
