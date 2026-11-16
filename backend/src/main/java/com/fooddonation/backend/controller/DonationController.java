package com.fooddonation.backend.controller;

import com.fooddonation.backend.dto.*;
import com.fooddonation.backend.service.DonationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

    @Autowired private DonationService donationService;

    // POST /api/donations — Donor creates donation
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DonationResponse> create(
            @Valid @RequestPart("data") DonationRequest req,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @AuthenticationPrincipal UserDetails user) throws Exception {
        return ResponseEntity.ok(donationService.createDonation(req, user.getUsername(), image));
    }

    // GET /api/donations/{id}
    @GetMapping("/{id}")
    public ResponseEntity<DonationResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getDonationById(id, user.getUsername()));
    }

    // GET /api/donations/mine — Donor's donations (paginated)
    @GetMapping("/mine")
    public ResponseEntity<List<DonationResponse>> mine(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getMyDonationsList(user.getUsername()));
    }

    // GET /api/donations/nearby — NGO discovery
    @GetMapping("/nearby")
    public ResponseEntity<List<DonationResponse>> nearby(
            @RequestParam(defaultValue = "5") Double radiusKm,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getNearbyDonations(user.getUsername(), radiusKm));
    }

    // POST /api/donations/{id}/accept — NGO accepts
    @PostMapping("/{id}/accept")
    public ResponseEntity<DonationResponse> accept(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "5") Double radiusKm,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.acceptDonation(id, user.getUsername(), radiusKm));
    }

    // POST /api/donations/{id}/retry-email — Retry donor notification email
    @PostMapping("/{id}/retry-email")
    public ResponseEntity<DonationResponse> retryEmail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.retryAcceptanceEmail(id, user.getUsername()));
    }

    // POST /api/donations/{id}/pickup — NGO confirms pickup
    @PostMapping("/{id}/pickup")
    public ResponseEntity<DonationResponse> pickup(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        String note = body != null ? body.getOrDefault("note", "") : "";
        return ResponseEntity.ok(donationService.markPickedUp(id, user.getUsername(), note));
    }

    // POST /api/donations/{id}/deliver — NGO confirms delivery
    @PostMapping("/{id}/deliver")
    public ResponseEntity<DonationResponse> deliver(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserDetails user) {
        Integer servings = body != null && body.get("actualServings") != null
                ? ((Number) body.get("actualServings")).intValue() : null;
        String notes = body != null ? (String) body.get("completionNotes") : null;
        return ResponseEntity.ok(donationService.markDelivered(id, user.getUsername(), servings, notes));
    }

    // POST /api/donations/{id}/cancel — Donor cancels
    @PostMapping("/{id}/cancel")
    public ResponseEntity<DonationResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(donationService.cancelDonation(id, user.getUsername(), reason));
    }

    // GET /api/donations/{id}/history — Status audit trail
    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusHistoryResponse>> history(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(donationService.getStatusHistory(id, user.getUsername()));
    }
}
