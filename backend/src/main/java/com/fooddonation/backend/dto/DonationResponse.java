package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.Donation;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class DonationResponse {
    private Long id;

    // Donor info (non-sensitive)
    private Long donorId;
    private String donorName;

    // Assigned NGO
    private Long assignedNgoId;
    private String assignedNgoName;

    // Food details
    private String foodName;
    private String description;
    private Donation.FoodCategory category;
    private Double quantity;
    private Donation.QuantityUnit quantityUnit;
    private Integer servings;
    private LocalDateTime preparationTime;
    private LocalDateTime pickupDeadline;
    private String handlingDetails;
    private Donation.DietaryType dietaryType;
    private String allergenInfo;
    private String imageUrl;

    // Pickup location (city/address only; exact coords only for accepted NGO)
    private String pickupAddress;
    private Double pickupLatitude;   // null for unrelated NGOs
    private Double pickupLongitude;  // null for unrelated NGOs

    private String contactInstructions;

    // Status
    private Donation.DonationStatus status;
    private Boolean safetyConfirmed;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiredAt;

    private String cancellationReason;
    private Integer actualServingsDelivered;
    private String completionNotes;

    // Distance (NGO search result only)
    private Double distanceKm;

    public static DonationResponse fromDonation(Donation d) {
        return fromDonation(d, true);
    }

    public static DonationResponse fromDonation(Donation d, boolean includeExactCoords) {
        return DonationResponse.builder()
                .id(d.getId())
                .donorId(d.getDonor().getId())
                .donorName(d.getDonor().getName())
                .assignedNgoId(d.getAssignedNgo() != null ? d.getAssignedNgo().getId() : null)
                .assignedNgoName(d.getAssignedNgo() != null ? d.getAssignedNgo().getName() : null)
                .foodName(d.getFoodName())
                .description(d.getDescription())
                .category(d.getCategory())
                .quantity(d.getQuantity())
                .quantityUnit(d.getQuantityUnit())
                .servings(d.getServings())
                .preparationTime(d.getPreparationTime())
                .pickupDeadline(d.getPickupDeadline())
                .handlingDetails(d.getHandlingDetails())
                .dietaryType(d.getDietaryType())
                .allergenInfo(d.getAllergenInfo())
                .imageUrl(d.getImageUrl())
                .pickupAddress(d.getPickupAddress())
                .pickupLatitude(includeExactCoords ? d.getPickupLatitude() : null)
                .pickupLongitude(includeExactCoords ? d.getPickupLongitude() : null)
                .contactInstructions(includeExactCoords ? d.getContactInstructions() : null)
                .status(d.getStatus())
                .safetyConfirmed(d.getSafetyConfirmed())
                .createdAt(d.getCreatedAt())
                .acceptedAt(d.getAcceptedAt())
                .pickedUpAt(d.getPickedUpAt())
                .deliveredAt(d.getDeliveredAt())
                .cancelledAt(d.getCancelledAt())
                .expiredAt(d.getExpiredAt())
                .cancellationReason(d.getCancellationReason())
                .actualServingsDelivered(d.getActualServingsDelivered())
                .completionNotes(d.getCompletionNotes())
                .build();
    }
}
