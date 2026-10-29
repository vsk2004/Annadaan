package com.fooddonation.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "donations", indexes = {
    @Index(name = "idx_don_donor",    columnList = "donor_id"),
    @Index(name = "idx_don_ngo",      columnList = "assigned_ngo_id"),
    @Index(name = "idx_don_status",   columnList = "status"),
    @Index(name = "idx_don_deadline", columnList = "pickup_deadline"),
    @Index(name = "idx_don_loc",      columnList = "pickup_latitude,pickup_longitude")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_ngo_id")
    private User assignedNgo;

    // Food details
    @Column(nullable = false)
    private String foodName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;

    @Column(nullable = false)
    private Double quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuantityUnit quantityUnit;

    private Integer servings;

    private LocalDateTime preparationTime;

    @Column(nullable = false)
    private LocalDateTime pickupDeadline;

    @Column(columnDefinition = "TEXT")
    private String handlingDetails;

    @Enumerated(EnumType.STRING)
    private DietaryType dietaryType;

    @Column(columnDefinition = "TEXT")
    private String allergenInfo;

    private String imageUrl;

    // Pickup location
    @Column(nullable = false)
    private String pickupAddress;

    @Column(nullable = false)
    private Double pickupLatitude;

    @Column(nullable = false)
    private Double pickupLongitude;

    private String contactInstructions;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DonationStatus status = DonationStatus.AVAILABLE;

    // Food safety
    @Builder.Default
    private Boolean safetyConfirmed = false;

    // Timestamps for each transition
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiredAt;

    private String cancellationReason;

    // Delivery notes
    private Integer actualServingsDelivered;
    @Column(columnDefinition = "TEXT")
    private String completionNotes;

    // Optimistic locking — prevents concurrent acceptance
    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Enums ─────────────────────────────────────────────────

    public enum FoodCategory {
        COOKED_MEALS, RICE_BIRYANI, BREAD, FRUITS, VEGETABLES, PACKAGED_FOOD, OTHER
    }

    public enum QuantityUnit {
        MEALS, PACKETS, KG, LITRES, BOXES, OTHER
    }

    public enum DietaryType {
        VEGETARIAN, NON_VEGETARIAN, VEGAN, NOT_SPECIFIED
    }

    public enum DonationStatus {
        AVAILABLE, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED, EXPIRED
    }
}
