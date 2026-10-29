package com.fooddonation.backend.service;

import com.fooddonation.backend.dto.*;
import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.exception.ConflictException;
import com.fooddonation.backend.exception.ForbiddenException;
import com.fooddonation.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DonationService {

    private static final Logger log = LoggerFactory.getLogger(DonationService.class);
    private static final Set<Double> ALLOWED_RADII = Set.of(5.0, 10.0);

    @Autowired private DonationRepository donationRepository;
    @Autowired private DonationStatusHistoryRepository historyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NgoProfileRepository ngoProfileRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    // ── Helper ─────────────────────────────────────────────────

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    private void recordHistory(Donation donation, DonationStatus prev, DonationStatus next,
                               User actor, String note) {
        historyRepository.save(DonationStatusHistory.builder()
                .donation(donation)
                .previousStatus(prev)
                .newStatus(next)
                .changedBy(actor)
                .note(note)
                .build());
    }

    // ── DONOR ──────────────────────────────────────────────────

    @Transactional
    public DonationResponse createDonation(DonationRequest req, String donorEmail,
                                           MultipartFile image) throws IOException {
        if (req.getPreparationTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Preparation time cannot be in the future");
        }

        int hours = req.getPickupWindowHours() != null ? req.getPickupWindowHours() : 3;
        LocalDateTime computedDeadline = req.getPickupDeadline() != null
                ? req.getPickupDeadline()
                : req.getPreparationTime().plusHours(hours);

        req.setPickupDeadline(computedDeadline);

        if (req.getPickupDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Pickup deadline must be today or in the future");
        }
        if (req.getPickupDeadline().isBefore(req.getPreparationTime())) {
            throw new IllegalArgumentException("Pickup deadline must be on or after preparation time");
        }

        User donor = requireUser(donorEmail);
        String imageUrl = image != null && !image.isEmpty() ? saveImage(image) : null;

        Donation donation = Donation.builder()
                .donor(donor)
                .foodName(req.getFoodName())
                .description(req.getDescription())
                .category(req.getCategory())
                .quantity(req.getQuantity())
                .quantityUnit(req.getQuantityUnit())
                .servings(req.getServings())
                .preparationTime(req.getPreparationTime())
                .pickupDeadline(req.getPickupDeadline())
                .handlingDetails(req.getHandlingDetails())
                .dietaryType(req.getDietaryType())
                .allergenInfo(req.getAllergenInfo())
                .imageUrl(imageUrl)
                .pickupAddress(req.getPickupAddress())
                .pickupLatitude(req.getPickupLatitude())
                .pickupLongitude(req.getPickupLongitude())
                .contactInstructions(req.getContactInstructions())
                .safetyConfirmed(req.getSafetyConfirmed())
                .status(DonationStatus.AVAILABLE)
                .build();

        donation = donationRepository.save(donation);
        recordHistory(donation, null, DonationStatus.AVAILABLE, donor, "Donation posted");
        return DonationResponse.fromDonation(donation);
    }

    public Page<DonationResponse> getMyDonations(String donorEmail, Pageable pageable) {
        User donor = requireUser(donorEmail);
        return donationRepository.findByDonorIdOrderByCreatedAtDesc(donor.getId(), pageable)
                .map(DonationResponse::fromDonation);
    }

    public List<DonationResponse> getMyDonationsList(String donorEmail) {
        User donor = requireUser(donorEmail);
        return donationRepository.findByDonorIdOrderByCreatedAtDesc(donor.getId())
                .stream().map(DonationResponse::fromDonation).toList();
    }

    public Map<String, Long> getDonorStats(String donorEmail) {
        User donor = requireUser(donorEmail);
        long id = donor.getId();
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",     donationRepository.countByDonorId(id));
        stats.put("available", donationRepository.countByDonorIdAndStatus(id, DonationStatus.AVAILABLE));
        stats.put("accepted",  donationRepository.countByDonorIdAndStatus(id, DonationStatus.ACCEPTED));
        stats.put("pickedUp",  donationRepository.countByDonorIdAndStatus(id, DonationStatus.PICKED_UP));
        stats.put("delivered", donationRepository.countByDonorIdAndStatus(id, DonationStatus.DELIVERED));
        stats.put("cancelled", donationRepository.countByDonorIdAndStatus(id, DonationStatus.CANCELLED));
        stats.put("expired",   donationRepository.countByDonorIdAndStatus(id, DonationStatus.EXPIRED));
        return stats;
    }

    @Transactional
    public DonationResponse cancelDonation(Long donationId, String donorEmail, String reason) {
        User donor = requireUser(donorEmail);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found"));

        if (!donation.getDonor().getId().equals(donor.getId())) {
            throw new ForbiddenException("You do not own this donation");
        }
        if (donation.getStatus() != DonationStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Only AVAILABLE donations can be cancelled by the donor. " +
                    "Current status: " + donation.getStatus());
        }

        DonationStatus prev = donation.getStatus();
        donation.setStatus(DonationStatus.CANCELLED);
        donation.setCancelledAt(LocalDateTime.now());
        donation.setCancellationReason(reason);
        donationRepository.save(donation);
        recordHistory(donation, prev, DonationStatus.CANCELLED, donor, reason);
        return DonationResponse.fromDonation(donation);
    }

    public DonationResponse getDonationById(Long id, String requesterEmail) {
        User requester = requireUser(requesterEmail);
        Donation donation = donationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Donation not found"));

        // Donors can only see their own donations' exact details;
        // NGOs can see details of assigned donations
        boolean isOwner = donation.getDonor().getId().equals(requester.getId());
        boolean isAssignedNgo = donation.getAssignedNgo() != null &&
                donation.getAssignedNgo().getId().equals(requester.getId());
        boolean includeCoords = isOwner || isAssignedNgo || requester.getRole() == User.Role.ADMIN;

        return DonationResponse.fromDonation(donation, includeCoords);
    }

    // ── NGO — Nearby ───────────────────────────────────────────

    public List<DonationResponse> getNearbyDonations(String ngoEmail, Double radiusKm) {
        if (!ALLOWED_RADII.contains(radiusKm)) {
            throw new IllegalArgumentException("Radius must be 5 or 10 km");
        }
        NgoProfile profile = ngoProfileRepository.findByUserEmail(ngoEmail)
                .orElseThrow(() -> new RuntimeException("NGO profile not found"));

        if (profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenException("Only verified NGOs can view nearby donations");
        }
        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            throw new IllegalStateException("NGO location not configured — please update your profile");
        }

        List<Object[]> rows = donationRepository.findNearbyAvailableDonations(
                profile.getLatitude(), profile.getLongitude(), radiusKm);

        List<DonationResponse> results = new ArrayList<>();
        for (Object[] row : rows) {
            Long donationId = ((Number) row[0]).longValue();
            donationRepository.findById(donationId).ifPresent(d -> {
                DonationResponse resp = DonationResponse.fromDonation(d, false); // no exact coords for browse
                double dist = row[row.length - 1] != null
                        ? Math.round(((Number) row[row.length - 1]).doubleValue() * 10.0) / 10.0
                        : 0.0;
                resp.setDistanceKm(dist);
                results.add(resp);
            });
        }
        return results;
    }

    // ── NGO — Atomic Accept ────────────────────────────────────

    @Transactional
    public DonationResponse acceptDonation(Long donationId, String ngoEmail, Double radiusKm) {
        if (radiusKm == null) radiusKm = 5.0;
        if (!ALLOWED_RADII.contains(radiusKm)) {
            throw new IllegalArgumentException("Radius must be 5 or 10 km");
        }

        NgoProfile profile = ngoProfileRepository.findByUserEmail(ngoEmail)
                .orElseThrow(() -> new RuntimeException("NGO profile not found"));

        if (profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenException("Only verified NGOs can accept donations");
        }
        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            throw new IllegalStateException("NGO location not set");
        }

        // Optimistic lock — prevents race condition
        Donation donation;
        try {
            donation = donationRepository.findByIdWithLock(donationId)
                    .orElseThrow(() -> new RuntimeException("Donation not found"));
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("Sorry, this food donation was just claimed by another NGO.");
        }

        // Guard: must still be AVAILABLE
        if (donation.getStatus() != DonationStatus.AVAILABLE) {
            throw new ConflictException("Sorry, this food donation has already been claimed or is no longer available.");
        }

        // Guard: must not be expired
        if (donation.getPickupDeadline().isBefore(LocalDateTime.now())) {
            donation.setStatus(DonationStatus.EXPIRED);
            donation.setExpiredAt(LocalDateTime.now());
            donationRepository.save(donation);
            throw new ConflictException("This donation has expired and can no longer be accepted.");
        }

        // Guard: distance check
        double distance = haversineKm(
                profile.getLatitude(), profile.getLongitude(),
                donation.getPickupLatitude(), donation.getPickupLongitude()
        );
        if (distance > radiusKm) {
            throw new ForbiddenException(String.format(
                    "This donation is %.1f km away — outside your selected radius of %.0f km.", distance, radiusKm));
        }

        DonationStatus prev = donation.getStatus();
        donation.setAssignedNgo(profile.getUser());
        donation.setStatus(DonationStatus.ACCEPTED);
        donation.setAcceptedAt(LocalDateTime.now());
        donationRepository.save(donation);

        recordHistory(donation, prev, DonationStatus.ACCEPTED, profile.getUser(),
                "Accepted by NGO: " + profile.getOrganizationName());

        DonationResponse resp = DonationResponse.fromDonation(donation, true);
        resp.setDistanceKm(Math.round(distance * 10.0) / 10.0);
        return resp;
    }

    // ── NGO — Pickup ───────────────────────────────────────────

    @Transactional
    public DonationResponse markPickedUp(Long donationId, String ngoEmail, String note) {
        User ngo = requireUser(ngoEmail);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found"));

        assertAssignedNgo(donation, ngo);

        if (donation.getStatus() != DonationStatus.ACCEPTED) {
            throw new IllegalStateException("Only ACCEPTED donations can be marked as PICKED_UP. Current: " + donation.getStatus());
        }

        DonationStatus prev = donation.getStatus();
        donation.setStatus(DonationStatus.PICKED_UP);
        donation.setPickedUpAt(LocalDateTime.now());
        donationRepository.save(donation);
        recordHistory(donation, prev, DonationStatus.PICKED_UP, ngo, note);
        return DonationResponse.fromDonation(donation, true);
    }

    // ── NGO — Deliver ──────────────────────────────────────────

    @Transactional
    public DonationResponse markDelivered(Long donationId, String ngoEmail,
                                          Integer actualServings, String completionNotes) {
        User ngo = requireUser(ngoEmail);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found"));

        assertAssignedNgo(donation, ngo);

        if (donation.getStatus() != DonationStatus.PICKED_UP) {
            throw new IllegalStateException("Only PICKED_UP donations can be marked DELIVERED. Current: " + donation.getStatus());
        }

        DonationStatus prev = donation.getStatus();
        donation.setStatus(DonationStatus.DELIVERED);
        donation.setDeliveredAt(LocalDateTime.now());
        donation.setActualServingsDelivered(actualServings);
        donation.setCompletionNotes(completionNotes);
        donationRepository.save(donation);
        recordHistory(donation, prev, DonationStatus.DELIVERED, ngo,
                "Delivered. " + (completionNotes != null ? completionNotes : ""));
        return DonationResponse.fromDonation(donation, true);
    }

    private void assertAssignedNgo(Donation donation, User ngo) {
        if (donation.getAssignedNgo() == null || !donation.getAssignedNgo().getId().equals(ngo.getId())) {
            throw new ForbiddenException("Only the assigned NGO can perform this action");
        }
    }

    // ── NGO — Lists ────────────────────────────────────────────

    public List<DonationResponse> getNgoActiveDonations(String ngoEmail) {
        User ngo = requireUser(ngoEmail);
        return donationRepository
                .findByAssignedNgoIdOrderByAcceptedAtDesc(ngo.getId())
                .stream()
                .filter(d -> d.getStatus() == DonationStatus.ACCEPTED || d.getStatus() == DonationStatus.PICKED_UP)
                .map(d -> DonationResponse.fromDonation(d, true))
                .toList();
    }

    public List<DonationResponse> getNgoHistory(String ngoEmail) {
        User ngo = requireUser(ngoEmail);
        return donationRepository
                .findByAssignedNgoIdAndStatusOrderByAcceptedAtDesc(ngo.getId(), DonationStatus.DELIVERED)
                .stream()
                .map(d -> DonationResponse.fromDonation(d, true))
                .toList();
    }

    public Map<String, Long> getNgoStats(String ngoEmail) {
        User ngo = requireUser(ngoEmail);
        long id = ngo.getId();
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("active",    donationRepository.countByAssignedNgoIdAndStatus(id, DonationStatus.ACCEPTED)
                             + donationRepository.countByAssignedNgoIdAndStatus(id, DonationStatus.PICKED_UP));
        stats.put("delivered", donationRepository.countByAssignedNgoIdAndStatus(id, DonationStatus.DELIVERED));
        return stats;
    }

    // ── Status History ─────────────────────────────────────────

    public List<StatusHistoryResponse> getStatusHistory(Long donationId, String requesterEmail) {
        User requester = requireUser(requesterEmail);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found"));

        // Only owner, assigned NGO, or ADMIN can view history
        boolean allowed = donation.getDonor().getId().equals(requester.getId())
                || (donation.getAssignedNgo() != null && donation.getAssignedNgo().getId().equals(requester.getId()))
                || requester.getRole() == User.Role.ADMIN;
        if (!allowed) throw new ForbiddenException("Not authorized to view history");

        return historyRepository.findByDonationIdOrderByChangedAtAsc(donationId)
                .stream()
                .map(h -> StatusHistoryResponse.builder()
                        .id(h.getId())
                        .donationId(donationId)
                        .previousStatus(h.getPreviousStatus())
                        .newStatus(h.getNewStatus())
                        .changedByUserId(h.getChangedBy().getId())
                        .changedByName(h.getChangedBy().getName())
                        .note(h.getNote())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();
    }

    // ── Scheduled Expiry ───────────────────────────────────────

    @Scheduled(fixedDelay = 60_000) // every minute
    @Transactional
    public void expireOverdueDonations() {
        List<Donation> expired = donationRepository.findExpiredAvailable(LocalDateTime.now());
        if (expired.isEmpty()) return;
        log.info("Expiring {} overdue donations", expired.size());

        // system user — use a placeholder approach
        for (Donation d : expired) {
            d.setStatus(DonationStatus.EXPIRED);
            d.setExpiredAt(LocalDateTime.now());
            donationRepository.save(d);
            recordHistory(d, DonationStatus.AVAILABLE, DonationStatus.EXPIRED,
                    d.getDonor(), // record against donor for audit
                    "Auto-expired: pickup deadline passed");
        }
    }

    // ── Image Save ─────────────────────────────────────────────

    private String saveImage(MultipartFile file) throws IOException {
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are accepted");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("Image must be under 10 MB");
        }
        Path path = Paths.get(uploadDir);
        if (!Files.exists(path)) Files.createDirectories(path);
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        Files.copy(file.getInputStream(), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + filename;
    }

    // ── Haversine Formula ──────────────────────────────────────

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                 + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
