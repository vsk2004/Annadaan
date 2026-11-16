package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.Donation;
import com.fooddonation.backend.entity.EmailNotificationLog;
import com.fooddonation.backend.entity.NgoProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DonationResponse {
    private Long id;

    // Donor info (non-sensitive)
    private Long donorId;
    private String donorName;

    // Assigned NGO
    private Long assignedNgoId;
    private String assignedNgoName;
    private String assignedNgoContactPerson;
    private String assignedNgoEmail;
    private String assignedNgoPhone;
    private String assignedNgoAddress;

    // Email notification tracking
    private String emailNotificationStatus;
    private String emailFailureReason;

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
        return fromDonation(d, includeExactCoords, null, null);
    }

    public static DonationResponse fromDonation(Donation d, boolean includeExactCoords,
                                               NgoProfile ngoProfile, EmailNotificationLog emailLog) {
        String ngoName = d.getAssignedNgo() != null ? d.getAssignedNgo().getName() : null;
        String contactPerson = null;
        String ngoEmail = null;
        String ngoPhone = null;
        String ngoAddress = null;

        if (d.getAssignedNgo() != null) {
            ngoEmail = d.getAssignedNgo().getEmail();
            ngoPhone = d.getAssignedNgo().getPhone();
            if (ngoProfile != null) {
                if (ngoProfile.getOrganizationName() != null && !ngoProfile.getOrganizationName().isBlank()) {
                    ngoName = ngoProfile.getOrganizationName();
                }
                contactPerson = ngoProfile.getContactPerson();
                StringBuilder addr = new StringBuilder();
                if (ngoProfile.getAddress() != null && !ngoProfile.getAddress().isBlank()) {
                    addr.append(ngoProfile.getAddress().trim());
                }
                if (ngoProfile.getCity() != null && !ngoProfile.getCity().isBlank()) {
                    if (!addr.isEmpty()) addr.append(", ");
                    addr.append(ngoProfile.getCity().trim());
                }
                if (ngoProfile.getState() != null && !ngoProfile.getState().isBlank()) {
                    if (!addr.isEmpty()) addr.append(", ");
                    addr.append(ngoProfile.getState().trim());
                }
                if (ngoProfile.getPostalCode() != null && !ngoProfile.getPostalCode().isBlank()) {
                    if (!addr.isEmpty()) addr.append(" - ");
                    addr.append(ngoProfile.getPostalCode().trim());
                }
                ngoAddress = !addr.isEmpty() ? addr.toString() : null;
            }
        }

        String emailStatus = emailLog != null ? emailLog.getDeliveryStatus().name() : null;
        String failureReason = emailLog != null ? emailLog.getFailureReason() : null;

        return DonationResponse.builder()
                .id(d.getId())
                .donorId(d.getDonor().getId())
                .donorName(d.getDonor().getName())
                .assignedNgoId(d.getAssignedNgo() != null ? d.getAssignedNgo().getId() : null)
                .assignedNgoName(ngoName)
                .assignedNgoContactPerson(contactPerson)
                .assignedNgoEmail(ngoEmail)
                .assignedNgoPhone(ngoPhone)
                .assignedNgoAddress(ngoAddress)
                .emailNotificationStatus(emailStatus)
                .emailFailureReason(failureReason)
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
