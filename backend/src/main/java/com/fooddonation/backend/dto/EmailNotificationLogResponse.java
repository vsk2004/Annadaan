package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.EmailNotificationLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationLogResponse {
    private Long id;
    private Long donationId;
    private String foodName;
    private String recipientEmail;
    private String subject;
    private String deliveryStatus;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public static EmailNotificationLogResponse fromEntity(EmailNotificationLog log) {
        if (log == null) return null;
        return EmailNotificationLogResponse.builder()
                .id(log.getId())
                .donationId(log.getDonation() != null ? log.getDonation().getId() : null)
                .foodName(log.getDonation() != null ? log.getDonation().getFoodName() : null)
                .recipientEmail(log.getRecipientEmail())
                .subject(log.getSubject())
                .deliveryStatus(log.getDeliveryStatus() != null ? log.getDeliveryStatus().name() : null)
                .failureReason(log.getFailureReason())
                .createdAt(log.getCreatedAt())
                .sentAt(log.getSentAt())
                .build();
    }
}
