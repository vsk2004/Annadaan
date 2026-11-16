package com.fooddonation.backend.service;

import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.EmailNotificationLog.DeliveryStatus;
import com.fooddonation.backend.exception.ForbiddenException;
import com.fooddonation.backend.repository.DonationRepository;
import com.fooddonation.backend.repository.EmailNotificationLogRepository;
import com.fooddonation.backend.repository.NgoProfileRepository;
import com.fooddonation.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        return emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(donationId);
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
