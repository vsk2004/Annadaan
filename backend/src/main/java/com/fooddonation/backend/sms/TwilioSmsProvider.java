package com.fooddonation.backend.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddonation.backend.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

@Component
public class TwilioSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsProvider.class);
    public static final String PROVIDER_NAME = "TWILIO";
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a, dd MMM");

    private final RestClient restClient;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String messagingServiceSid;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    @Autowired
    public TwilioSmsProvider(
            RestClient.Builder restClientBuilder,
            @Value("${twilio.account-sid:${TWILIO_ACCOUNT_SID:}}") String accountSid,
            @Value("${twilio.auth-token:${TWILIO_AUTH_TOKEN:}}") String authToken,
            @Value("${twilio.from-number:${TWILIO_FROM_NUMBER:}}") String fromNumber,
            @Value("${twilio.messaging-service-sid:${TWILIO_MESSAGING_SERVICE_SID:}}") String messagingServiceSid,
            @Value("${twilio.api-url:}") String apiUrl,
            ObjectMapper objectMapper) {
        this(buildRestClient(restClientBuilder), accountSid, authToken, fromNumber, messagingServiceSid, apiUrl, objectMapper);
    }

    public TwilioSmsProvider(
            RestClient restClient,
            String accountSid,
            String authToken,
            String fromNumber,
            String messagingServiceSid,
            String apiUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.accountSid = accountSid != null ? accountSid.trim() : "";
        this.authToken = authToken != null ? authToken.trim() : "";
        this.fromNumber = fromNumber != null ? fromNumber.trim() : "";
        this.messagingServiceSid = messagingServiceSid != null ? messagingServiceSid.trim() : "";
        this.apiUrl = apiUrl != null ? apiUrl.trim() : "";
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
        return !accountSid.isBlank() && !authToken.isBlank() && (!fromNumber.isBlank() || !messagingServiceSid.isBlank());
    }

    @Override
    public SmsSendResult sendDonationAlert(SmsSendRequest request) {
        if (!isAvailable()) {
            log.warn("Twilio provider is not configured. Missing accountSid, authToken, or fromNumber/messagingServiceSid.");
            return SmsSendResult.failure("Twilio configuration incomplete", PROVIDER_NAME);
        }

        String normalizedMobile = PhoneUtils.normalizeForTwilio(request.getRecipientPhone());
        if (normalizedMobile == null) {
            log.warn("Cannot send Twilio SMS: invalid mobile format for recipient {}", request.getMaskedPhone());
            return SmsSendResult.failure("Invalid mobile phone number format", PROVIDER_NAME);
        }

        String messageBody = buildMessageBody(request);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("To", normalizedMobile);
        if (!fromNumber.isBlank()) {
            formData.add("From", fromNumber);
        } else if (!messagingServiceSid.isBlank()) {
            formData.add("MessagingServiceSid", messagingServiceSid);
        }
        formData.add("Body", messageBody);

        String targetUrl = resolveEndpointUrl();
        String masked = request.getMaskedPhone() != null
                ? request.getMaskedPhone()
                : PhoneUtils.maskPhone(normalizedMobile);

        try {
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                    (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            String responseBody = restClient.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            String providerMessageId = extractMessageId(responseBody);
            log.info("Twilio SMS accepted for recipient {} (donation #{}, sid: {})",
                    masked, request.getDonationId(), providerMessageId);

            return SmsSendResult.success(providerMessageId, PROVIDER_NAME);
        } catch (RestClientResponseException e) {
            String errorDetail = extractErrorDetail(e.getResponseBodyAsString(), e.getStatusCode().value(), e.getStatusText());
            log.error("Twilio rejected SMS for {} with HTTP {}: {}",
                    masked, e.getStatusCode().value(), errorDetail);
            return SmsSendResult.failure("Twilio HTTP " + e.getStatusCode().value() + ": " + errorDetail, PROVIDER_NAME);
        } catch (RestClientException e) {
            log.error("Twilio request failed for {}: {}", masked, e.getMessage());
            return SmsSendResult.failure("Twilio connection error: " + e.getMessage(), PROVIDER_NAME);
        }
    }

    public String buildMessageBody(SmsSendRequest request) {
        if (request.getTemplateVariables() != null && request.getTemplateVariables().containsKey("body")) {
            return request.getTemplateVariables().get("body");
        }
        if (request.getTemplateVariables() != null && request.getTemplateVariables().containsKey("message")) {
            return request.getTemplateVariables().get("message");
        }

        String food = request.getFoodName() != null && !request.getFoodName().isBlank()
                ? request.getFoodName()
                : "Food donation";

        StringBuilder sb = new StringBuilder();
        sb.append("Annadaan Alert: ").append(food);

        if (request.getQuantity() != null) {
            String qtyStr = (request.getQuantity() % 1 == 0)
                    ? String.valueOf(request.getQuantity().intValue())
                    : String.valueOf(request.getQuantity());
            sb.append(" (").append(qtyStr);
            if (request.getUnit() != null && !request.getUnit().isBlank()) {
                sb.append(" ").append(request.getUnit());
            }
            sb.append(")");
        }

        if (request.getDistanceKm() != null) {
            sb.append(String.format(Locale.US, " is available ~%.1f km away.", request.getDistanceKm()));
        } else {
            sb.append(" is available nearby.");
        }

        if (request.getPickupDeadline() != null) {
            sb.append(" Pickup before: ").append(request.getPickupDeadline().format(DEADLINE_FORMATTER)).append(".");
        }

        if (request.getAppLink() != null && !request.getAppLink().isBlank()) {
            sb.append(" Claim: ").append(request.getAppLink());
        }

        return sb.toString();
    }

    private String resolveEndpointUrl() {
        if (!apiUrl.isBlank()) {
            if (apiUrl.contains("{AccountSid}")) {
                return apiUrl.replace("{AccountSid}", accountSid);
            }
            if (apiUrl.contains("{accountSid}")) {
                return apiUrl.replace("{accountSid}", accountSid);
            }
            return apiUrl;
        }
        return "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "twilio-unknown";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("sid")) {
                return root.get("sid").asText();
            }
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return "twilio-accepted";
    }

    private String extractErrorDetail(String responseBody, int statusCode, String defaultStatusText) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.hasNonNull("message")) {
                    return root.get("message").asText();
                }
            } catch (Exception ignored) {
            }
        }
        return defaultStatusText != null ? defaultStatusText : String.valueOf(statusCode);
    }
}
