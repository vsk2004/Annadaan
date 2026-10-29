package com.fooddonation.backend.dto;

import com.fooddonation.backend.entity.NgoProfile;
import com.fooddonation.backend.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private User.Role role;
    private User.AccountStatus accountStatus;
    private LocalDateTime createdAt;

    // NGO profile (populated only for NGO users)
    private NgoProfileDto ngoProfile;

    @Data @Builder
    public static class NgoProfileDto {
        private Long id;
        private String organizationName;
        private String organizationType;
        private String contactPerson;
        private String registrationNumber;
        private NgoProfile.VerificationStatus verificationStatus;
        private String address;
        private String city;
        private String state;
        private String postalCode;
        private Double latitude;
        private Double longitude;
    }

    public static UserResponse fromUser(User user, NgoProfile ngoProfile) {
        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .createdAt(user.getCreatedAt());

        if (ngoProfile != null) {
            builder.ngoProfile(NgoProfileDto.builder()
                    .id(ngoProfile.getId())
                    .organizationName(ngoProfile.getOrganizationName())
                    .organizationType(ngoProfile.getOrganizationType())
                    .contactPerson(ngoProfile.getContactPerson())
                    .registrationNumber(ngoProfile.getRegistrationNumber())
                    .verificationStatus(ngoProfile.getVerificationStatus())
                    .address(ngoProfile.getAddress())
                    .city(ngoProfile.getCity())
                    .state(ngoProfile.getState())
                    .postalCode(ngoProfile.getPostalCode())
                    .latitude(ngoProfile.getLatitude())
                    .longitude(ngoProfile.getLongitude())
                    .build());
        }
        return builder.build();
    }
}
