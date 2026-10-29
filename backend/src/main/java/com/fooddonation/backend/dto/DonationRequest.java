package com.fooddonation.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fooddonation.backend.entity.Donation;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DonationRequest {

    @NotBlank(message = "Food name is required")
    private String foodName;

    private String description;

    @NotNull(message = "Category is required")
    private Donation.FoodCategory category;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    @NotNull(message = "Quantity unit is required")
    private Donation.QuantityUnit quantityUnit;

    private Integer servings;

    @NotNull(message = "Preparation time is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime preparationTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime pickupDeadline;

    @Min(value = 1, message = "Pickup window must be at least 1 hour")
    @Max(value = 12, message = "Pickup window cannot exceed 12 hours")
    private Integer pickupWindowHours;

    private String handlingDetails;

    private Donation.DietaryType dietaryType;

    private String allergenInfo;

    @NotBlank(message = "Pickup address is required")
    private String pickupAddress;

    @NotNull(message = "Pickup latitude is required")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    private Double pickupLongitude;

    private String contactInstructions;

    @NotNull(message = "You must confirm food safety")
    @AssertTrue(message = "You must confirm the food has been safely handled")
    private Boolean safetyConfirmed;
}
