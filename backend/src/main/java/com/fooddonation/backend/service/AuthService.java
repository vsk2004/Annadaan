package com.fooddonation.backend.service;

import com.fooddonation.backend.dto.*;
import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.User.Role;
import com.fooddonation.backend.repository.*;
import com.fooddonation.backend.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private NgoProfileRepository ngoProfileRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private AuthenticationManager authenticationManager;

    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null) return null;
        return rawPhone.trim().replaceAll("[\\s()\\-]", "");
    }

    private void notifyNearbyVerifiedNgos(User donor, Double donorLat, Double donorLng, String donorLocationLabel) {
        if (donorLat == null || donorLng == null) return;

        String locationLabel = donorLocationLabel != null && !donorLocationLabel.isBlank()
                ? donorLocationLabel
                : "your area";

        List<NgoProfile> nearbyNgos = ngoProfileRepository.findAll().stream()
                .filter(profile -> profile.getVerificationStatus() == NgoProfile.VerificationStatus.VERIFIED)
                .filter(profile -> profile.getLatitude() != null && profile.getLongitude() != null)
                .filter(profile -> haversineDistanceKm(donorLat, donorLng, profile.getLatitude(), profile.getLongitude()) <= 10.0)
                .toList();

        for (NgoProfile profile : nearbyNgos) {
            double distanceKm = haversineDistanceKm(donorLat, donorLng, profile.getLatitude(), profile.getLongitude());
            String message = donor.getName() + " registered as a donor near " + locationLabel
                    + ". Please contact them at " + donor.getPhone() + " or " + donor.getEmail() + ".";

            notificationRepository.save(Notification.builder()
                    .recipient(profile.getUser())
                    .donor(donor)
                    .title("New donor nearby")
                    .message(message)
                    .distanceKm(Math.round(distanceKm * 10.0) / 10.0)
                    .build());
        }
    }

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";
    public static final String PASSWORD_REQUIREMENT_MESSAGE = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character";

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.getPhone() != null) {
            req.setPhone(normalizePhone(req.getPhone()));
        }
        if (req.getEmail() != null) {
            req.setEmail(req.getEmail().trim().toLowerCase(Locale.ROOT));
        }

        if (req.getPassword() == null || !req.getPassword().matches(PASSWORD_REGEX)) {
            throw new IllegalArgumentException(PASSWORD_REQUIREMENT_MESSAGE);
        }
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (req.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Admin registration is not permitted via this endpoint");
        }
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("Phone number is already registered");
        }

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .build();
        user = userRepository.save(user);

        NgoProfile ngoProfile = null;
        if (req.getRole() == Role.NGO) {
            ngoProfile = NgoProfile.builder()
                    .user(user)
                    .organizationName(req.getOrganizationName() != null ? req.getOrganizationName() : req.getName())
                    .organizationType(req.getOrganizationType())
                    .contactPerson(req.getContactPerson())
                    .registrationNumber(req.getRegistrationNumber())
                    .ngoRegistrationInfo(req.getNgoRegistrationInfo())
                    .address(req.getAddress())
                    .city(req.getCity())
                    .state(req.getState())
                    .postalCode(req.getPostalCode())
                    .latitude(req.getLatitude())
                    .longitude(req.getLongitude())
                    .verificationStatus(NgoProfile.VerificationStatus.VERIFIED)
                    .build();
            ngoProfileRepository.save(ngoProfile);
        }

        if (user.getRole() == Role.DONOR) {
            notifyNearbyVerifiedNgos(user, req.getLatitude(), req.getLongitude(), req.getCity() != null ? req.getCity() : req.getAddress());
        }

        String token = jwtUtils.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.fromUser(user, ngoProfile))
                .build();
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().trim();
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, req.getPassword()));

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        NgoProfile ngoProfile = user.getRole() == Role.NGO
                ? ngoProfileRepository.findByUserId(user.getId()).orElse(null)
                : null;

        String token = jwtUtils.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.fromUser(user, ngoProfile))
                .build();
    }

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new RuntimeException("User not found"));
        NgoProfile ngoProfile = user.getRole() == Role.NGO
                ? ngoProfileRepository.findByUserId(user.getId()).orElse(null)
                : null;
        return UserResponse.fromUser(user, ngoProfile);
    }
}
