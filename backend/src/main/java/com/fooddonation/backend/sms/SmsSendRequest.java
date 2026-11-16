package com.fooddonation.backend.sms;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class SmsSendRequest {
    private String recipientPhone;
    private String maskedPhone;
    private Long donationId;
    private String foodName;
    private Double quantity;
    private String unit;
    private Double distanceKm;
    private LocalDateTime pickupDeadline;
    private String appLink;
    private Map<String, String> templateVariables;
}
