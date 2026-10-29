package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.Donation.DonationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class StatusHistoryResponse {
    private Long id;
    private Long donationId;
    private DonationStatus previousStatus;
    private DonationStatus newStatus;
    private Long changedByUserId;
    private String changedByName;
    private String note;
    private LocalDateTime changedAt;
}
