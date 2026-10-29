package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private String donorName;
    private String donorEmail;
    private String donorPhone;
    private Double distanceKm;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse fromNotification(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .donorName(notification.getDonor() != null ? notification.getDonor().getName() : null)
                .donorEmail(notification.getDonor() != null ? notification.getDonor().getEmail() : null)
                .donorPhone(notification.getDonor() != null ? notification.getDonor().getPhone() : null)
                .distanceKm(notification.getDistanceKm())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
