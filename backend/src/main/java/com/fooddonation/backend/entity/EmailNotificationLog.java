package com.fooddonation.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "email_notification_logs",
    indexes = {
        @Index(name = "idx_email_log_donation", columnList = "donation_id"),
        @Index(name = "idx_email_log_recipient", columnList = "recipient_id"),
        @Index(name = "idx_email_log_status", columnList = "delivery_status"),
        @Index(name = "idx_email_log_created_at", columnList = "created_at")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailNotificationLog {

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
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Builder.Default
    private int attemptCount = 0;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    private String providerMessageId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime lastAttemptAt;

    public enum DeliveryStatus {
        PENDING,
        SENT,
        FAILED
    }
}
