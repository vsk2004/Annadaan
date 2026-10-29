package com.fooddonation.backend.controller;

import com.fooddonation.backend.dto.*;
import com.fooddonation.backend.entity.NgoProfile;
import com.fooddonation.backend.entity.Notification;
import com.fooddonation.backend.repository.NgoProfileRepository;
import com.fooddonation.backend.repository.NotificationRepository;
import com.fooddonation.backend.service.DonationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ngo")
@PreAuthorize("hasRole('NGO')")
public class NgoController {

    @Autowired private DonationService donationService;
    @Autowired private NgoProfileRepository ngoProfileRepository;
    @Autowired private NotificationRepository notificationRepository;

    @GetMapping("/profile")
    public ResponseEntity<UserResponse.NgoProfileDto> getProfile(@AuthenticationPrincipal UserDetails user) {
        NgoProfile profile = ngoProfileRepository.findByUserEmailIgnoreCase(user.getUsername())
                .orElseThrow(() -> new RuntimeException("NGO profile not found"));
        return ResponseEntity.ok(toProfileResponse(profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse.NgoProfileDto> updateProfile(
            @RequestBody NgoProfileUpdateRequest req,
            @AuthenticationPrincipal UserDetails user) {
        NgoProfile profile = ngoProfileRepository.findByUserEmailIgnoreCase(user.getUsername())
                .orElseThrow(() -> new RuntimeException("NGO profile not found"));

        if (req.getOrganizationName() != null) profile.setOrganizationName(req.getOrganizationName());
        if (req.getOrganizationType() != null) profile.setOrganizationType(req.getOrganizationType());
        if (req.getContactPerson() != null)    profile.setContactPerson(req.getContactPerson());
        if (req.getAddress() != null)          profile.setAddress(req.getAddress());
        if (req.getCity() != null)             profile.setCity(req.getCity());
        if (req.getState() != null)            profile.setState(req.getState());
        if (req.getPostalCode() != null)       profile.setPostalCode(req.getPostalCode());
        if (req.getLatitude() != null)         profile.setLatitude(req.getLatitude());
        if (req.getLongitude() != null)        profile.setLongitude(req.getLongitude());

        return ResponseEntity.ok(toProfileResponse(ngoProfileRepository.save(profile)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getNgoStats(user.getUsername()));
    }

    @GetMapping("/active-donations")
    public ResponseEntity<List<DonationResponse>> active(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getNgoActiveDonations(user.getUsername()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<DonationResponse>> history(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getNgoHistory(user.getUsername()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> notifications(@AuthenticationPrincipal UserDetails user) {
        NgoProfile profile = ngoProfileRepository.findByUserEmailIgnoreCase(user.getUsername())
                .orElseThrow(() -> new RuntimeException("NGO profile not found"));

        List<NotificationResponse> response = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(profile.getUser().getId())
                .stream()
                .map(NotificationResponse::fromNotification)
                .toList();

        return ResponseEntity.ok(response);
    }

    private UserResponse.NgoProfileDto toProfileResponse(NgoProfile profile) {
        return UserResponse.NgoProfileDto.builder()
                .id(profile.getId())
                .organizationName(profile.getOrganizationName())
                .organizationType(profile.getOrganizationType())
                .contactPerson(profile.getContactPerson())
                .registrationNumber(profile.getRegistrationNumber())
                .verificationStatus(profile.getVerificationStatus())
                .address(profile.getAddress())
                .city(profile.getCity())
                .state(profile.getState())
                .postalCode(profile.getPostalCode())
                .latitude(profile.getLatitude())
                .longitude(profile.getLongitude())
                .build();
    }
}
