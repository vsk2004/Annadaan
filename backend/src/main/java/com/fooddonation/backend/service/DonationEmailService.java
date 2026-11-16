package com.fooddonation.backend.service;

import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.EmailNotificationLog.DeliveryStatus;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.entity.User.AccountStatus;
import com.fooddonation.backend.entity.User.Role;
import com.fooddonation.backend.exception.ForbiddenException;
import com.fooddonation.backend.repository.DonationRepository;
import com.fooddonation.backend.repository.EmailNotificationLogRepository;
import com.fooddonation.backend.repository.NgoProfileRepository;
import com.fooddonation.backend.repository.UserRepository;
import com.fooddonation.backend.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DonationEmailService {

    private static final Logger log = LoggerFactory.getLogger(DonationEmailService.class);
    private static final String SUBJECT = "Your Annadaan Donation Has Been Accepted!";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    @Autowired private DonationRepository donationRepository;
    @Autowired private NgoProfileRepository ngoProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailNotificationLogRepository emailNotificationLogRepository;
    @Autowired private BrevoTransactionalEmailSender emailSender;

    @Value("${app.email.notification-radius-km:${app.sms.notification-radius-km:10.0}}")
    private Double emailNotificationRadiusKm = 10.0;

    @Value("${app.frontend.url:${app.base-url:http://localhost:5173}}")
    private String frontendUrl = "http://localhost:5173";

    @Transactional
    public EmailNotificationLog sendAcceptanceEmailNotification(Long donationId) {
        return sendAcceptanceEmailNotification(donationId, false);
    }

    @Transactional
    public EmailNotificationLog sendAcceptanceEmailNotification(Long donationId, boolean forceRetry) {
        if (donationId == null) {
            log.warn("Cannot send acceptance email: donationId is null");
            return null;
        }

        Donation donation = donationRepository.findById(donationId).orElse(null);
        if (donation == null) {
            log.error("Donation #{} not found; cannot send acceptance email", donationId);
            return null;
        }

        // Verify status: must be ACCEPTED, PICKED_UP, or DELIVERED
        if (donation.getStatus() != DonationStatus.ACCEPTED &&
                donation.getStatus() != DonationStatus.PICKED_UP &&
                donation.getStatus() != DonationStatus.DELIVERED) {
            log.warn("Donation #{} status is {} (not ACCEPTED/PICKED_UP/DELIVERED); skipping acceptance email",
                    donationId, donation.getStatus());
            return null;
        }

        // Idempotency: Prevent duplicate emails if already SENT, unless explicitly forced
        if (!forceRetry && emailNotificationLogRepository.existsByDonationIdAndDeliveryStatus(donationId, DeliveryStatus.SENT)) {
            log.info("Idempotency: Acceptance email has already been successfully sent for donation #{}; skipping duplicate",
                    donationId);
            return emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(donationId).orElse(null);
        }

        User donor = donation.getDonor();
        if (donor == null) {
            log.error("Donation #{} has no donor associated; cannot send acceptance email", donationId);
            return null;
        }

        String donorEmail = donor.getEmail();
        String donorName = donor.getName();

        // Get or initialize log entry
        EmailNotificationLog logEntry = emailNotificationLogRepository
                .findFirstByDonationIdOrderByCreatedAtDesc(donationId)
                .filter(l -> l.getDeliveryStatus() != DeliveryStatus.SENT)
                .orElseGet(() -> EmailNotificationLog.builder()
                        .donation(donation)
                        .recipient(donor)
                        .recipientEmail(donorEmail != null ? donorEmail : "")
                        .subject(SUBJECT)
                        .deliveryStatus(DeliveryStatus.PENDING)
                        .attemptCount(0)
                        .build());

        logEntry.setAttemptCount(logEntry.getAttemptCount() + 1);
        logEntry.setLastAttemptAt(LocalDateTime.now());

        // Validate donor email address
        if (donorEmail == null || donorEmail.isBlank() || !EMAIL_PATTERN.matcher(donorEmail.trim()).matches()) {
            String reason = "Donor email address is missing or invalid: '" + donorEmail + "'";
            log.error("Donation #{}: {}", donationId, reason);
            logEntry.setDeliveryStatus(DeliveryStatus.FAILED);
            logEntry.setFailureReason(reason);
            return emailNotificationLogRepository.save(logEntry);
        }

        logEntry.setRecipientEmail(donorEmail.trim());

        // Fetch assigned NGO user and profile
        User ngoUser = donation.getAssignedNgo();
        if (ngoUser == null) {
            String reason = "No NGO assigned to donation #" + donationId;
            log.error(reason);
            logEntry.setDeliveryStatus(DeliveryStatus.FAILED);
            logEntry.setFailureReason(reason);
            return emailNotificationLogRepository.save(logEntry);
        }

        NgoProfile ngoProfile = ngoProfileRepository.findByUserEmail(ngoUser.getEmail()).orElse(null);

        // Prepare email data
        String foodName = donation.getFoodName();
        String description = (donation.getDescription() != null && !donation.getDescription().isBlank())
                ? donation.getDescription() : "None provided";
        String quantity = donation.getQuantity() + " " +
                (donation.getQuantityUnit() != null ? donation.getQuantityUnit().name() : "");
        String category = formatCategory(donation.getCategory());
        String pickupLocation = donation.getPickupAddress();
        String donationIdStr = String.valueOf(donation.getId());

        String ngoName = (ngoProfile != null && ngoProfile.getOrganizationName() != null)
                ? ngoProfile.getOrganizationName() : ngoUser.getName();
        String contactPerson = (ngoProfile != null && ngoProfile.getContactPerson() != null && !ngoProfile.getContactPerson().isBlank())
                ? ngoProfile.getContactPerson() : ngoUser.getName();
        String ngoEmail = ngoUser.getEmail();
        String ngoPhone = (ngoUser.getPhone() != null && !ngoUser.getPhone().isBlank())
                ? ngoUser.getPhone() : "Not provided";
        String ngoAddress = formatAddress(ngoProfile);
        String ngoLocation = formatLocation(ngoProfile);

        LocalDateTime acceptedAt = donation.getAcceptedAt() != null ? donation.getAcceptedAt() : LocalDateTime.now();
        String acceptedDate = acceptedAt.format(DATE_FORMATTER);
        String acceptedTime = acceptedAt.format(TIME_FORMATTER);
        String currentStatus = donation.getStatus() != null ? donation.getStatus().name() : "ACCEPTED";

        String textContent = buildPlainTextContent(
                donorName, foodName, description, quantity, category, pickupLocation, donationIdStr,
                ngoName, contactPerson, ngoEmail, ngoPhone, ngoAddress, ngoLocation,
                acceptedDate, acceptedTime, currentStatus
        );

        String htmlContent = buildHtmlContent(
                donorName, foodName, description, quantity, category, pickupLocation, donationIdStr,
                ngoName, contactPerson, ngoEmail, ngoPhone, ngoAddress, ngoLocation,
                acceptedDate, acceptedTime, currentStatus
        );

        try {
            String providerMessageId = emailSender.sendEmail(donorEmail.trim(), donorName, SUBJECT, htmlContent, textContent);
            logEntry.setDeliveryStatus(DeliveryStatus.SENT);
            logEntry.setProviderMessageId(providerMessageId);
            logEntry.setSentAt(LocalDateTime.now());
            logEntry.setFailureReason(null);
            log.info("Successfully delivered donation acceptance email to donor {} for donation #{}", donorEmail, donationId);
        } catch (Exception ex) {
            log.error("Failed to deliver donation acceptance email to donor {} for donation #{}: {}",
                    donorEmail, donationId, ex.getMessage(), ex);
            logEntry.setDeliveryStatus(DeliveryStatus.FAILED);
            logEntry.setFailureReason(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }

        return emailNotificationLogRepository.save(logEntry);
    }

    @Transactional
    public EmailNotificationLog retryAcceptanceEmail(Long donationId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + requesterEmail));

        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found: " + donationId));

        boolean isDonor = donation.getDonor() != null && donation.getDonor().getId().equals(requester.getId());
        boolean isNgo = donation.getAssignedNgo() != null && donation.getAssignedNgo().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == User.Role.ADMIN;

        if (!isDonor && !isNgo && !isAdmin) {
            throw new ForbiddenException("You are not authorized to retry notifications for this donation");
        }

        if (donation.getStatus() != DonationStatus.ACCEPTED &&
                donation.getStatus() != DonationStatus.PICKED_UP &&
                donation.getStatus() != DonationStatus.DELIVERED) {
            throw new IllegalStateException("Can only retry notifications for accepted donations (current status: " + donation.getStatus() + ")");
        }

        return sendAcceptanceEmailNotification(donationId, true);
    }

    public Optional<EmailNotificationLog> getLatestEmailStatus(Long donationId) {
        if (donationId == null) return Optional.empty();
        Donation donation = donationRepository.findById(donationId).orElse(null);
        if (donation != null && donation.getDonor() != null) {
            Optional<EmailNotificationLog> donorLog = emailNotificationLogRepository
                    .findFirstByDonationIdAndRecipientIdOrderByCreatedAtDesc(donationId, donation.getDonor().getId());
            if (donorLog.isPresent()) {
                return donorLog;
            }
        }
        return emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(donationId);
    }

    // ── Nearby NGO Email Dispatch Workflow ─────────────────────

    @Transactional
    public List<EmailNotificationLog> sendNearbyDonationAlertEmails(Long donationId) {
        if (donationId == null) {
            log.warn("Cannot send nearby NGO alert emails: donationId is null");
            return Collections.emptyList();
        }

        Donation donation = donationRepository.findById(donationId).orElse(null);
        if (donation == null) {
            log.error("Donation #{} not found; cannot send nearby NGO alert emails", donationId);
            return Collections.emptyList();
        }

        if (donation.getStatus() != DonationStatus.AVAILABLE) {
            log.info("Donation #{} status is {} (not AVAILABLE); skipping nearby NGO alert emails",
                    donationId, donation.getStatus());
            return Collections.emptyList();
        }

        if (donation.getPickupDeadline() != null && donation.getPickupDeadline().isBefore(LocalDateTime.now())) {
            log.info("Donation #{} has already expired; skipping nearby NGO alert emails", donationId);
            return Collections.emptyList();
        }

        Double donLat = donation.getPickupLatitude();
        Double donLng = donation.getPickupLongitude();
        if (donLat == null || donLng == null) {
            log.warn("Donation #{} has no pickup coordinates; skipping nearby NGO alert emails", donationId);
            return Collections.emptyList();
        }

        double radiusKm = emailNotificationRadiusKm != null ? emailNotificationRadiusKm : 10.0;
        List<NgoProfile> allNgos = ngoProfileRepository.findAll();
        List<EmailNotificationLog> dispatchedLogs = new ArrayList<>();

        for (NgoProfile profile : allNgos) {
            try {
                if (!isEligibleNgoForEmail(profile, donLat, donLng, radiusKm)) {
                    continue;
                }

                User ngoUser = profile.getUser();

                if (emailNotificationLogRepository.existsByDonationIdAndRecipientIdAndDeliveryStatus(
                        donation.getId(), ngoUser.getId(), DeliveryStatus.SENT)) {
                    log.info("Idempotency: Email alert already sent for donation #{} to NGO {}; skipping duplicate",
                            donation.getId(), ngoUser.getId());
                    continue;
                }

                Donation current = donationRepository.findById(donation.getId()).orElse(null);
                if (current == null || current.getStatus() != DonationStatus.AVAILABLE ||
                        (current.getPickupDeadline() != null && current.getPickupDeadline().isBefore(LocalDateTime.now()))) {
                    log.info("Donation #{} is no longer available (current status: {}); cancelling remaining alert emails",
                            donation.getId(), current != null ? current.getStatus() : "null");
                    break;
                }

                double distanceKm = GeoUtils.haversineKm(donLat, donLng, profile.getLatitude(), profile.getLongitude());
                EmailNotificationLog logEntry = sendAlertEmailToNgo(donation, ngoUser, profile, distanceKm);
                if (logEntry != null) {
                    dispatchedLogs.add(logEntry);
                }
            } catch (Exception ex) {
                log.error("Failed to process nearby email notification for NGO profile #{}: {}",
                        profile.getId(), ex.getMessage(), ex);
            }
        }

        log.info("Finished nearby NGO email notification workflow for donation #{}. Notified {} eligible NGO(s)",
                donationId, dispatchedLogs.size());
        return dispatchedLogs;
    }

    private boolean isEligibleNgoForEmail(NgoProfile profile, double donLat, double donLng, double radiusKm) {
        if (profile == null) return false;

        if (profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return false;
        }

        if (!profile.isEmailNotificationsEnabled()) {
            return false;
        }

        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            return false;
        }

        User user = profile.getUser();
        if (user == null || user.getRole() != Role.NGO || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }

        if (user.getEmail() == null || user.getEmail().isBlank() || !EMAIL_PATTERN.matcher(user.getEmail().trim()).matches()) {
            return false;
        }

        double distanceKm = GeoUtils.haversineKm(donLat, donLng, profile.getLatitude(), profile.getLongitude());
        return distanceKm <= radiusKm;
    }

    private EmailNotificationLog sendAlertEmailToNgo(Donation donation, User ngoUser, NgoProfile profile, double distanceKm) {
        String subject = "Annadaan Alert: New Food Donation Available Nearby! (" + donation.getFoodName() + ")";
        String ngoEmail = ngoUser.getEmail().trim();
        String ngoName = (profile.getOrganizationName() != null && !profile.getOrganizationName().isBlank())
                ? profile.getOrganizationName().trim() : ngoUser.getName();
        String contactPerson = (profile.getContactPerson() != null && !profile.getContactPerson().isBlank())
                ? profile.getContactPerson().trim() : ngoName;

        EmailNotificationLog logEntry = emailNotificationLogRepository
                .findFirstByDonationIdAndRecipientIdOrderByCreatedAtDesc(donation.getId(), ngoUser.getId())
                .filter(l -> l.getDeliveryStatus() != DeliveryStatus.SENT)
                .orElseGet(() -> EmailNotificationLog.builder()
                        .donation(donation)
                        .recipient(ngoUser)
                        .recipientEmail(ngoEmail)
                        .subject(subject)
                        .deliveryStatus(DeliveryStatus.PENDING)
                        .attemptCount(0)
                        .build());

        logEntry.setAttemptCount(logEntry.getAttemptCount() + 1);
        logEntry.setLastAttemptAt(LocalDateTime.now());
        logEntry.setSubject(subject);
        logEntry.setRecipientEmail(ngoEmail);

        String claimUrl = (frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "http://localhost:5173")
                + "/donations/" + donation.getId();

        String textContent = buildNearbyNgoAlertPlainText(donation, ngoName, contactPerson, distanceKm, claimUrl);
        String htmlContent = buildNearbyNgoAlertHtml(donation, ngoName, contactPerson, distanceKm, claimUrl);

        try {
            String providerMessageId = emailSender.sendEmail(ngoEmail, ngoName, subject, htmlContent, textContent);
            logEntry.setDeliveryStatus(DeliveryStatus.SENT);
            logEntry.setProviderMessageId(providerMessageId);
            logEntry.setSentAt(LocalDateTime.now());
            logEntry.setFailureReason(null);
            log.info("Successfully delivered nearby donation alert email to NGO {} ({}) for donation #{}",
                    ngoEmail, ngoName, donation.getId());
        } catch (Exception ex) {
            log.error("Failed to deliver nearby donation alert email to NGO {} ({}) for donation #{}: {}",
                    ngoEmail, ngoName, donation.getId(), ex.getMessage(), ex);
            logEntry.setDeliveryStatus(DeliveryStatus.FAILED);
            logEntry.setFailureReason(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }

        return emailNotificationLogRepository.save(logEntry);
    }

    public String buildNearbyNgoAlertPlainText(
            Donation donation, String ngoName, String contactPerson, double distanceKm, String claimUrl) {

        String recipientGreeting = (contactPerson != null && !contactPerson.isBlank())
                ? "Dear " + contactPerson + " (" + ngoName + "),"
                : "Dear " + ngoName + " Team,";

        String foodName = donation.getFoodName() != null ? donation.getFoodName() : "Food Donation";
        String description = (donation.getDescription() != null && !donation.getDescription().isBlank())
                ? donation.getDescription() : "None provided";
        String quantity = donation.getQuantity() + " " +
                (donation.getQuantityUnit() != null ? donation.getQuantityUnit().name() : "");
        String servings = donation.getServings() != null ? ("~" + donation.getServings() + " people") : "Not specified";
        String category = formatCategory(donation.getCategory());
        String dietary = donation.getDietaryType() != null ? donation.getDietaryType().name().replace('_', ' ') : "Not specified";
        String allergens = (donation.getAllergenInfo() != null && !donation.getAllergenInfo().isBlank())
                ? donation.getAllergenInfo() : "None mentioned";
        String handling = (donation.getHandlingDetails() != null && !donation.getHandlingDetails().isBlank())
                ? donation.getHandlingDetails() : "Standard handling";
        String pickupAddress = donation.getPickupAddress() != null ? donation.getPickupAddress() : "Available upon claim";
        String deadline = donation.getPickupDeadline() != null
                ? donation.getPickupDeadline().format(DATE_FORMATTER) + " at " + donation.getPickupDeadline().format(TIME_FORMATTER)
                : "Not specified";
        String prepTime = donation.getPreparationTime() != null
                ? donation.getPreparationTime().format(DATE_FORMATTER) + " at " + donation.getPreparationTime().format(TIME_FORMATTER)
                : "Not specified";

        return recipientGreeting + "\n\n"
                + "A new surplus food donation is available nearby (~" + String.format("%.1f", distanceKm) + " km away)!\n\n"
                + "Here are the donation details:\n\n"
                + "Food Donation Details\n"
                + "- Food Name: " + foodName + "\n"
                + "- Food Category: " + category + "\n"
                + "- Quantity: " + quantity + " (" + servings + ")\n"
                + "- Dietary Type: " + dietary + "\n"
                + "- Allergen Info: " + allergens + "\n"
                + "- Description: " + description + "\n"
                + "- Storage/Handling: " + handling + "\n"
                + "- Prepared At: " + prepTime + "\n\n"
                + "Pickup & Location Details\n"
                + "- Distance: ~" + String.format("%.1f", distanceKm) + " km from your NGO location\n"
                + "- Pickup Address: " + pickupAddress + "\n"
                + "- Pickup Deadline: " + deadline + "\n\n"
                + "To view full details and claim this donation, please visit:\n"
                + claimUrl + "\n\n"
                + "Thank you for partnering with Annadaan to reduce food waste and feed those in need.\n\n"
                + "Regards,\n"
                + "Team Annadaan\n";
    }

    public String buildNearbyNgoAlertHtml(
            Donation donation, String ngoName, String contactPerson, double distanceKm, String claimUrl) {

        String recipientGreeting = (contactPerson != null && !contactPerson.isBlank())
                ? "Dear " + escapeHtml(contactPerson) + " (" + escapeHtml(ngoName) + "),"
                : "Dear " + escapeHtml(ngoName) + " Team,";

        String foodName = donation.getFoodName() != null ? escapeHtml(donation.getFoodName()) : "Food Donation";
        String description = (donation.getDescription() != null && !donation.getDescription().isBlank())
                ? escapeHtml(donation.getDescription()) : "None provided";
        String quantity = donation.getQuantity() + " " +
                (donation.getQuantityUnit() != null ? escapeHtml(donation.getQuantityUnit().name()) : "");
        String servings = donation.getServings() != null ? ("~" + donation.getServings() + " people") : "Not specified";
        String category = escapeHtml(formatCategory(donation.getCategory()));
        String dietary = donation.getDietaryType() != null ? escapeHtml(donation.getDietaryType().name().replace('_', ' ')) : "Not specified";
        String allergens = (donation.getAllergenInfo() != null && !donation.getAllergenInfo().isBlank())
                ? escapeHtml(donation.getAllergenInfo()) : "None mentioned";
        String handling = (donation.getHandlingDetails() != null && !donation.getHandlingDetails().isBlank())
                ? escapeHtml(donation.getHandlingDetails()) : "Standard handling";
        String pickupAddress = donation.getPickupAddress() != null ? escapeHtml(donation.getPickupAddress()) : "Available upon claim";
        String deadline = donation.getPickupDeadline() != null
                ? escapeHtml(donation.getPickupDeadline().format(DATE_FORMATTER) + ", " + donation.getPickupDeadline().format(TIME_FORMATTER))
                : "Not specified";
        String prepTime = donation.getPreparationTime() != null
                ? escapeHtml(donation.getPreparationTime().format(DATE_FORMATTER) + ", " + donation.getPreparationTime().format(TIME_FORMATTER))
                : "Not specified";
        String distStr = String.format("%.1f", distanceKm);
        String donationIdStr = String.valueOf(donation.getId());

        return String.format("""
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>New Food Donation Available Nearby!</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background-color: #f1f5f2;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      color: #1f2937;
      line-height: 1.6;
    }
    .wrapper {
      width: 100%%;
      background-color: #f1f5f2;
      padding: 30px 10px;
      box-sizing: border-box;
    }
    .container {
      max-width: 620px;
      margin: 0 auto;
      background: #ffffff;
      border-radius: 14px;
      overflow: hidden;
      box-shadow: 0 4px 18px rgba(0, 0, 0, 0.07);
      border: 1px solid #e5e7eb;
    }
    .header {
      background: linear-gradient(135deg, #1b4332 0%%, #2d6a4f 100%%);
      padding: 32px 28px;
      text-align: center;
      color: #ffffff;
    }
    .brand-logo {
      display: inline-block;
      font-size: 28px;
      font-weight: 800;
      letter-spacing: -0.5px;
      margin: 0;
      color: #ffffff;
    }
    .brand-leaf {
      color: #74c69d;
      margin-right: 6px;
    }
    .brand-tagline {
      margin: 6px 0 0;
      font-size: 13px;
      color: #b7e4c7;
      letter-spacing: 0.5px;
      text-transform: uppercase;
      font-weight: 600;
    }
    .hero {
      padding: 24px 28px 10px;
    }
    .distance-badge {
      display: inline-block;
      background: #d8f3dc;
      color: #1b4332;
      font-weight: 700;
      font-size: 13px;
      padding: 6px 14px;
      border-radius: 999px;
      border: 1px solid #b7e4c7;
      margin-bottom: 12px;
    }
    .hero-title {
      font-size: 20px;
      font-weight: 700;
      color: #1b4332;
      margin: 0 0 10px;
    }
    .hero-p {
      margin: 0 0 16px;
      font-size: 15px;
      color: #374151;
    }
    .card {
      margin: 0 28px 20px;
      background: #f8faf9;
      border-radius: 10px;
      border: 1px solid #e5e7eb;
      overflow: hidden;
    }
    .card-header {
      background: #edf5f0;
      padding: 12px 18px;
      font-size: 13px;
      font-weight: 700;
      color: #2d6a4f;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      border-bottom: 1px solid #e0ece4;
    }
    .data-table {
      width: 100%%;
      border-collapse: collapse;
    }
    .data-table td {
      padding: 10px 18px;
      font-size: 14px;
      border-bottom: 1px solid #edf2ee;
      vertical-align: top;
    }
    .data-table tr:last-child td {
      border-bottom: none;
    }
    .label {
      color: #6b7280;
      font-weight: 600;
      width: 38%%;
    }
    .value {
      color: #111827;
      font-weight: 500;
    }
    .cta-block {
      text-align: center;
      padding: 20px 28px 28px;
    }
    .btn {
      display: inline-block;
      background: #2d6a4f;
      color: #ffffff !important;
      text-decoration: none;
      font-weight: 700;
      font-size: 15px;
      padding: 14px 28px;
      border-radius: 8px;
      box-shadow: 0 2px 6px rgba(45, 106, 79, 0.3);
    }
    .footer {
      background: #fafbf9;
      border-top: 1px solid #e5e7eb;
      padding: 22px 28px;
      text-align: center;
      font-size: 12px;
      color: #6b7280;
      line-height: 1.5;
    }
  </style>
</head>
<body>
  <div class="wrapper">
    <div class="container">
      <div class="header">
        <h1 class="brand-logo"><span class="brand-leaf">&#127793;</span>Annadaan</h1>
        <p class="brand-tagline">Surplus Food Rescue &amp; Donation Network</p>
      </div>

      <div class="hero">
        <span class="distance-badge">&#128205; ~%s km away</span>
        <h2 class="hero-title">New Food Donation Available Nearby!</h2>
        <p class="hero-p">%s</p>
        <p class="hero-p">A donor has just posted a surplus food donation within your service radius. Review the details below to claim it for distribution:</p>
      </div>

      <div class="card">
        <div class="card-header">&#127828; Food Details</div>
        <table class="data-table">
          <tr><td class="label">Food Name</td><td class="value"><strong>%s</strong> (#%s)</td></tr>
          <tr><td class="label">Category</td><td class="value">%s</td></tr>
          <tr><td class="label">Quantity</td><td class="value">%s (%s)</td></tr>
          <tr><td class="label">Dietary Type</td><td class="value">%s</td></tr>
          <tr><td class="label">Allergen Info</td><td class="value">%s</td></tr>
          <tr><td class="label">Prepared At</td><td class="value">%s</td></tr>
          <tr><td class="label">Description</td><td class="value">%s</td></tr>
          <tr><td class="label">Storage/Handling</td><td class="value">%s</td></tr>
        </table>
      </div>

      <div class="card">
        <div class="card-header">&#128205; Pickup &amp; Timing</div>
        <table class="data-table">
          <tr><td class="label">Distance</td><td class="value">~%s km from your registered location</td></tr>
          <tr><td class="label">Pickup Address</td><td class="value">%s</td></tr>
          <tr><td class="label">Pickup Deadline</td><td class="value"><strong style="color: #b91c1c;">%s</strong></td></tr>
        </table>
      </div>

      <div class="cta-block">
        <a href="%s" class="btn">View &amp; Claim Donation</a>
      </div>

      <div class="footer">
        <p style="margin: 0 0 6px;">
          You received this alert because <strong>%s</strong> is verified on Annadaan and within matching distance of this donation.
        </p>
        <p style="margin: 0;">
          Manage your notification preferences anytime from your NGO Profile on Annadaan.
        </p>
      </div>
    </div>
  </div>
</body>
</html>
""",
                distStr,
                recipientGreeting,
                foodName,
                donationIdStr,
                category,
                quantity,
                servings,
                dietary,
                allergens,
                prepTime,
                description,
                handling,
                distStr,
                pickupAddress,
                deadline,
                claimUrl,
                escapeHtml(ngoName)
        );
    }

    // ── Helper formatters ──────────────────────────────────────

    private String formatCategory(Donation.FoodCategory category) {
        if (category == null) return "Not specified";
        return switch (category) {
            case COOKED_MEALS -> "Cooked Meals";
            case RICE_BIRYANI -> "Rice & Biryani";
            case BREAD -> "Bread & Bakery";
            case FRUITS -> "Fruits";
            case VEGETABLES -> "Vegetables";
            case PACKAGED_FOOD -> "Packaged Food";
            case OTHER -> "Other";
        };
    }

    private String formatAddress(NgoProfile profile) {
        if (profile == null) return "Not provided";
        StringBuilder sb = new StringBuilder();
        if (profile.getAddress() != null && !profile.getAddress().isBlank()) {
            sb.append(profile.getAddress().trim());
        }
        if (profile.getCity() != null && !profile.getCity().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(profile.getCity().trim());
        }
        if (profile.getState() != null && !profile.getState().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(profile.getState().trim());
        }
        if (profile.getPostalCode() != null && !profile.getPostalCode().isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(profile.getPostalCode().trim());
        }
        return !sb.isEmpty() ? sb.toString() : "Not provided";
    }

    private String formatLocation(NgoProfile profile) {
        if (profile == null || profile.getLatitude() == null || profile.getLongitude() == null) {
            return "Not available";
        }
        String coords = String.format("%.4f, %.4f", profile.getLatitude(), profile.getLongitude());
        if (profile.getCity() != null && !profile.getCity().isBlank()) {
            return profile.getCity().trim() + " (" + coords + ")";
        }
        return coords;
    }

    // ── Plain-Text Template ────────────────────────────────────

    public String buildPlainTextContent(
            String donorName, String foodName, String description, String quantity,
            String category, String pickupLocation, String donationId,
            String ngoName, String contactPerson, String ngoEmail, String ngoPhone,
            String ngoAddress, String ngoLocation,
            String acceptedDate, String acceptedTime, String currentStatus) {

        String greeting = (donorName != null && !donorName.isBlank()) ? "Dear " + donorName + "," : "Dear Donor,";

        return greeting + "\n\n"
                + "Great news! Your food donation has been successfully accepted by an NGO through Annadaan.\n\n"
                + "Here are the acceptance details:\n\n"
                + "Food Donation Details\n"
                + "- Food name: " + foodName + "\n"
                + "- Food description: " + description + "\n"
                + "- Quantity: " + quantity + "\n"
                + "- Food category: " + category + "\n"
                + "- Pickup location: " + pickupLocation + "\n"
                + "- Donation ID: #" + donationId + "\n\n"
                + "NGO Details\n"
                + "- NGO name: " + ngoName + "\n"
                + "- NGO representative/contact person name: " + contactPerson + "\n"
                + "- NGO registered email address: " + ngoEmail + "\n"
                + "- NGO contact number: " + ngoPhone + "\n"
                + "- NGO address: " + ngoAddress + "\n"
                + "- NGO location: " + ngoLocation + "\n\n"
                + "Acceptance Details\n"
                + "- Accepted date: " + acceptedDate + "\n"
                + "- Accepted time: " + acceptedTime + "\n"
                + "- Current donation status: " + currentStatus + "\n\n"
                + "Thank you for supporting Annadaan and helping reduce food waste. Please coordinate with the NGO using the contact details above to arrange the food pickup.\n\n"
                + "Regards,\n"
                + "Team Annadaan\n";
    }

    // ── HTML Template ──────────────────────────────────────────

    public String buildHtmlContent(
            String donorName, String foodName, String description, String quantity,
            String category, String pickupLocation, String donationId,
            String ngoName, String contactPerson, String ngoEmail, String ngoPhone,
            String ngoAddress, String ngoLocation,
            String acceptedDate, String acceptedTime, String currentStatus) {

        String greeting = (donorName != null && !donorName.isBlank()) ? "Dear " + escapeHtml(donorName) + "," : "Dear Donor,";

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Your Annadaan Donation Has Been Accepted!</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background-color: #f1f5f2;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      color: #1f2937;
      line-height: 1.6;
    }
    .wrapper {
      width: 100%%;
      background-color: #f1f5f2;
      padding: 30px 10px;
      box-sizing: border-box;
    }
    .container {
      max-width: 620px;
      margin: 0 auto;
      background: #ffffff;
      border-radius: 14px;
      overflow: hidden;
      box-shadow: 0 4px 18px rgba(0, 0, 0, 0.07);
      border: 1px solid #e5e7eb;
    }
    .header {
      background: linear-gradient(135deg, #1b4332 0%%, #2d6a4f 100%%);
      padding: 32px 28px;
      text-align: center;
      color: #ffffff;
    }
    .brand-logo {
      display: inline-block;
      font-size: 28px;
      font-weight: 800;
      letter-spacing: -0.5px;
      margin: 0;
      color: #ffffff;
    }
    .brand-leaf {
      color: #74c69d;
      margin-right: 6px;
    }
    .brand-tagline {
      margin: 6px 0 0;
      font-size: 13px;
      color: #b7e4c7;
      letter-spacing: 0.5px;
      text-transform: uppercase;
      font-weight: 600;
    }
    .banner {
      background-color: #d8f3dc;
      border-bottom: 2px solid #95d5b2;
      padding: 16px 24px;
      text-align: center;
    }
    .banner-title {
      margin: 0;
      color: #1b4332;
      font-size: 17px;
      font-weight: 700;
    }
    .content {
      padding: 28px;
    }
    .greeting {
      font-size: 17px;
      font-weight: 700;
      color: #111827;
      margin-top: 0;
      margin-bottom: 12px;
    }
    .intro {
      font-size: 15px;
      color: #374151;
      margin-top: 0;
      margin-bottom: 22px;
      line-height: 1.6;
    }
    .section-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      margin-bottom: 20px;
      overflow: hidden;
    }
    .section-header {
      background: #f8faf9;
      border-bottom: 1px solid #e2e8f0;
      padding: 12px 18px;
      font-size: 15px;
      font-weight: 700;
      color: #1b4332;
      display: flex;
      align-items: center;
    }
    .section-body {
      padding: 12px 18px;
    }
    .detail-table {
      width: 100%%;
      border-collapse: collapse;
    }
    .detail-table td {
      padding: 8px 0;
      font-size: 14px;
      vertical-align: top;
      border-bottom: 1px solid #f1f5f9;
    }
    .detail-table tr:last-child td {
      border-bottom: none;
    }
    .label-col {
      width: 42%%;
      color: #64748b;
      font-weight: 500;
    }
    .value-col {
      width: 58%%;
      color: #0f172a;
      font-weight: 600;
      text-align: right;
      word-break: break-word;
    }
    .badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.5px;
      background: #2d6a4f;
      color: #ffffff;
    }
    .badge-id {
      background: #e8f5e9;
      color: #1b4332;
      border: 1px solid #c8e6c9;
      padding: 3px 10px;
      border-radius: 6px;
      font-family: monospace;
    }
    .callout {
      background: #f0fdf4;
      border-left: 4px solid #2d6a4f;
      border-radius: 6px;
      padding: 16px 18px;
      margin: 24px 0;
      font-size: 14px;
      color: #166534;
      line-height: 1.6;
    }
    .closing {
      font-size: 14px;
      color: #374151;
      margin-top: 24px;
      line-height: 1.5;
    }
    .footer {
      background: #f8faf9;
      border-top: 1px solid #e5e7eb;
      padding: 20px 28px;
      text-align: center;
      font-size: 12px;
      color: #94a3b8;
    }
    a.link {
      color: #2d6a4f;
      text-decoration: none;
      font-weight: 600;
    }
    a.link:hover {
      text-decoration: underline;
    }
  </style>
</head>
<body>
  <div class="wrapper">
    <div class="container">
      <!-- Header -->
      <div class="header">
        <h1 class="brand-logo"><span class="brand-leaf">&#127793;</span>Annadaan!</h1>
        <p class="brand-tagline">Food Donation &amp; Waste Reduction Platform</p>
      </div>

      <!-- Banner -->
      <div class="banner">
        <p class="banner-title">&#10004; Your Food Donation Has Been Accepted!</p>
      </div>

      <!-- Content -->
      <div class="content">
        <p class="greeting">%s</p>
        <p class="intro">
          Great news! Your food donation has been successfully accepted by an NGO through Annadaan.<br>
          Here are the acceptance details:
        </p>

        <!-- Food Donation Details Card -->
        <div class="section-card">
          <div class="section-header">&#127828;&nbsp; Food Donation Details</div>
          <div class="section-body">
            <table class="detail-table">
              <tr>
                <td class="label-col">Donation ID</td>
                <td class="value-col"><span class="badge-id">#%s</span></td>
              </tr>
              <tr>
                <td class="label-col">Food Name</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Quantity</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Food Category</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Pickup Location</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Description</td>
                <td class="value-col">%s</td>
              </tr>
            </table>
          </div>
        </div>

        <!-- NGO Details Card -->
        <div class="section-card">
          <div class="section-header">&#127970;&nbsp; NGO Details</div>
          <div class="section-body">
            <table class="detail-table">
              <tr>
                <td class="label-col">NGO Name</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Contact Person</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Registered Email</td>
                <td class="value-col"><a href="mailto:%s" class="link">%s</a></td>
              </tr>
              <tr>
                <td class="label-col">Contact Number</td>
                <td class="value-col"><a href="tel:%s" class="link">%s</a></td>
              </tr>
              <tr>
                <td class="label-col">NGO Address</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">NGO Location</td>
                <td class="value-col">%s</td>
              </tr>
            </table>
          </div>
        </div>

        <!-- Acceptance Details Card -->
        <div class="section-card">
          <div class="section-header">&#9201;&nbsp; Acceptance Details</div>
          <div class="section-body">
            <table class="detail-table">
              <tr>
                <td class="label-col">Accepted Date</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Accepted Time</td>
                <td class="value-col">%s</td>
              </tr>
              <tr>
                <td class="label-col">Current Status</td>
                <td class="value-col"><span class="badge">%s</span></td>
              </tr>
            </table>
          </div>
        </div>

        <!-- Callout Message -->
        <div class="callout">
          <strong>Next step:</strong> Thank you for supporting Annadaan and helping reduce food waste. Please coordinate with the NGO using the contact details above to arrange the food pickup.
        </div>

        <!-- Closing -->
        <div class="closing">
          Regards,<br>
          <strong>Team Annadaan</strong>
        </div>
      </div>

      <!-- Footer -->
      <div class="footer">
        &copy; Annadaan Platform. Dedicated to zero food waste and zero hunger.
      </div>
    </div>
  </div>
</body>
</html>
""".formatted(
                greeting,
                escapeHtml(donationId),
                escapeHtml(foodName),
                escapeHtml(quantity),
                escapeHtml(category),
                escapeHtml(pickupLocation),
                escapeHtml(description),
                escapeHtml(ngoName),
                escapeHtml(contactPerson),
                escapeHtml(ngoEmail), escapeHtml(ngoEmail),
                escapeHtml(ngoPhone), escapeHtml(ngoPhone),
                escapeHtml(ngoAddress),
                escapeHtml(ngoLocation),
                escapeHtml(acceptedDate),
                escapeHtml(acceptedTime),
                escapeHtml(currentStatus)
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
