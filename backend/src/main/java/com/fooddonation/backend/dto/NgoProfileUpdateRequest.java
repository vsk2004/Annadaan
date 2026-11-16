package com.fooddonation.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class NgoProfileUpdateRequest {
    private String organizationName;
    private String organizationType;
    private String contactPerson;
    private String registrationNumber;
    private String ngoRegistrationInfo;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private Boolean smsNotificationsEnabled;
}
