package com.fooddonation.backend.entity;

import com.fooddonation.backend.entity.Donation.DonationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "donation_status_history", indexes = {
    @Index(name = "idx_hist_donation",  columnList = "donation_id"),
    @Index(name = "idx_hist_changedAt", columnList = "changed_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DonationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;

    @Enumerated(EnumType.STRING)
    private DonationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;
}
