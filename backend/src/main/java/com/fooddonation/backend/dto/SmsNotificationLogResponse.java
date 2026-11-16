package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.SmsNotificationLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsNotificationLogResponse {
    private Long id;
    private Long donationId;
    private String foodName;
    private Double quantity;
    private String quantityUnit;
    private Double distanceKm;
    private String channel;
    private String deliveryStatus;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public static SmsNotificationLogResponse fromEntity(SmsNotificationLog log) {
        if (log == null) {
            return null;
        }
        return SmsNotificationLogResponse.builder()
                .id(log.getId())
                .donationId(log.getDonation() != null ? log.getDonation().getId() : null)
                .foodName(log.getDonation() != null ? log.getDonation().getFoodName() : null)
                .quantity(log.getDonation() != null ? log.getDonation().getQuantity() : null)
                .quantityUnit(log.getDonation() != null && log.getDonation().getQuantityUnit() != null ? log.getDonation().getQuantityUnit().name() : null)
                .distanceKm(log.getDistanceKm())
                .channel(log.getChannel())
                .deliveryStatus(log.getDeliveryStatus() != null ? log.getDeliveryStatus().name() : null)
                .createdAt(log.getCreatedAt())
                .sentAt(log.getSentAt())
                .build();
    }
}
