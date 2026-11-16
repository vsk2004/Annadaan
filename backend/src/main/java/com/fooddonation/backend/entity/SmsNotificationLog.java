package com.fooddonation.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "sms_notification_logs",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_donation_recipient", columnNames = {"donation_id", "recipient_id"})
    },
    indexes = {
        @Index(name = "idx_sms_log_donation", columnList = "donation_id"),
        @Index(name = "idx_sms_log_recipient", columnList = "recipient_id"),
        @Index(name = "idx_sms_log_status", columnList = "delivery_status"),
        @Index(name = "idx_sms_log_created_at", columnList = "created_at")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SmsNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    @Builder.Default
    private String channel = "SMS";

    @Column(nullable = false)
    private String provider; // "TWILIO", "MSG91", "MOCK"

    private String messageTemplateId;

    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Builder.Default
    private int attemptCount = 0;

    private Double distanceKm;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime lastAttemptAt;

    public enum DeliveryStatus {
        PENDING,
        SENT,
        FAILED,
        DELIVERED,
        UNKNOWN
    }
}
