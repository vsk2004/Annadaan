package com.fooddonation.backend.listener;

import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.entity.User.AccountStatus;
import com.fooddonation.backend.entity.User.Role;
import com.fooddonation.backend.event.DonationCreatedEvent;
import com.fooddonation.backend.repository.DonationRepository;
import com.fooddonation.backend.repository.NgoProfileRepository;
import com.fooddonation.backend.repository.NotificationRepository;
import com.fooddonation.backend.repository.SmsNotificationLogRepository;
import com.fooddonation.backend.sms.SmsSendRequest;
import com.fooddonation.backend.sms.SmsSendResult;
import com.fooddonation.backend.sms.SmsService;
import com.fooddonation.backend.util.GeoUtils;
import com.fooddonation.backend.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DonationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DonationNotificationListener.class);

    @Autowired private DonationRepository donationRepository;
    @Autowired private NgoProfileRepository ngoProfileRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SmsNotificationLogRepository smsNotificationLogRepository;
    @Autowired private SmsService smsService;

    @Value("${app.base-url:${app.frontend.url:http://localhost:5173}}")
    private String appBaseUrl;

    @Value("${app.sms.notification-radius-km:10.0}")
    private Double defaultRadiusKm;

    @Value("${twilio.messaging-service-sid:${TWILIO_MESSAGING_SERVICE_SID:${msg91.sms-template-id:${MSG91_SMS_TEMPLATE_ID:}}}}")
    private String msg91TemplateId;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDonationCreated(DonationCreatedEvent event) {
        if (event == null || event.getDonationId() == null) {
            return;
        }

        Long donationId = event.getDonationId();
        log.info("Processing post-commit SMS notification workflow for donation #{}", donationId);

        Donation donation = donationRepository.findById(donationId).orElse(null);
        if (donation == null) {
            log.warn("Donation #{} not found; skipping notification processing", donationId);
            return;
        }

        if (donation.getStatus() != DonationStatus.AVAILABLE) {
            log.info("Donation #{} status is {} (not AVAILABLE); skipping notifications",
                    donationId, donation.getStatus());
            return;
        }

        if (donation.getPickupDeadline() != null && donation.getPickupDeadline().isBefore(LocalDateTime.now())) {
            log.info("Donation #{} has already expired; skipping notifications", donationId);
            return;
        }

        Double donLat = donation.getPickupLatitude();
        Double donLng = donation.getPickupLongitude();
        if (donLat == null || donLng == null) {
            log.warn("Donation #{} has no pickup coordinates; skipping notifications", donationId);
            return;
        }

        List<NgoProfile> allNgoProfiles = ngoProfileRepository.findAll();
        double radiusKm = defaultRadiusKm != null ? defaultRadiusKm : 10.0;
        int notifiedCount = 0;

        for (NgoProfile profile : allNgoProfiles) {
            try {
                if (!isEligibleNgo(profile, donLat, donLng, radiusKm)) {
                    continue;
                }

                User ngoUser = profile.getUser();
                if (smsNotificationLogRepository.existsByDonationIdAndRecipientId(donation.getId(), ngoUser.getId())) {
                    log.info("Idempotency: SMS already logged for donation #{} to NGO {}; skipping duplicate",
                            donation.getId(), ngoUser.getId());
                    continue;
                }

                Donation current = donationRepository.findById(donation.getId()).orElse(null);
                if (current == null || current.getStatus() != DonationStatus.AVAILABLE ||
                        (current.getPickupDeadline() != null && current.getPickupDeadline().isBefore(LocalDateTime.now()))) {
                    log.info("Donation #{} is no longer available (current status: {}); cancelling remaining alerts",
                            donation.getId(), current != null ? current.getStatus() : "null");
                    break;
                }

                double distanceKm = GeoUtils.haversineKm(donLat, donLng, profile.getLatitude(), profile.getLongitude());
                sendNotificationToNgo(donation, ngoUser, profile, distanceKm);
                notifiedCount++;
            } catch (Exception ex) {
                log.error("Failed to process notification for NGO profile #{}: {}",
                        profile.getId(), ex.getMessage(), ex);
            }
        }

        log.info("Finished SMS notification workflow for donation #{}. Notified {} eligible NGO(s)",
                donationId, notifiedCount);
    }

    private boolean isEligibleNgo(NgoProfile profile, double donLat, double donLng, double radiusKm) {
        if (profile == null) return false;

        if (profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return false;
        }

        if (!profile.isSmsNotificationsEnabled()) {
            return false;
        }

        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            return false;
        }

        User user = profile.getUser();
        if (user == null || user.getRole() != Role.NGO || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }

        if (user.getPhone() == null || user.getPhone().isBlank() || !PhoneUtils.isValidIndianMobile(user.getPhone())) {
            return false;
        }

        double distanceKm = GeoUtils.haversineKm(donLat, donLng, profile.getLatitude(), profile.getLongitude());
        return distanceKm <= radiusKm;
    }

    private void sendNotificationToNgo(Donation donation, User ngoUser, NgoProfile profile, double distanceKm) {
        if (!smsService.isSmsEnabled()) {
            log.info("SMS notifications are disabled; skipping SMS dispatch for recipient {}", ngoUser.getId());
        } else {
            String rawPhone = ngoUser.getPhone();
            String normalizedPhone = PhoneUtils.normalizeToE164(rawPhone);
            if (normalizedPhone == null) {
                normalizedPhone = PhoneUtils.normalizeIndianMobile(rawPhone);
            }
            String maskedPhone = PhoneUtils.maskPhone(normalizedPhone != null ? normalizedPhone : rawPhone);
            String appLink = (appBaseUrl != null ? appBaseUrl.replaceAll("/+$", "") : "http://localhost:5173") + "/ngo/dashboard";

            SmsNotificationLog logEntry = SmsNotificationLog.builder()
                    .donation(donation)
                    .recipient(ngoUser)
                    .phoneNumber(normalizedPhone != null ? normalizedPhone : rawPhone)
                    .channel("SMS")
                    .provider(smsService.getConfiguredProvider().toUpperCase())
                    .messageTemplateId(msg91TemplateId)
                    .distanceKm(distanceKm)
                    .deliveryStatus(SmsNotificationLog.DeliveryStatus.PENDING)
                    .attemptCount(1)
                    .lastAttemptAt(LocalDateTime.now())
                    .build();

            SmsSendRequest sendRequest = SmsSendRequest.builder()
                    .donationId(donation.getId())
                    .recipientPhone(normalizedPhone != null ? normalizedPhone : rawPhone)
                    .maskedPhone(maskedPhone)
                    .foodName(donation.getFoodName())
                    .quantity(donation.getQuantity())
                    .unit(donation.getQuantityUnit() != null ? donation.getQuantityUnit().name() : "")
                    .distanceKm(distanceKm)
                    .pickupDeadline(donation.getPickupDeadline())
                    .appLink(appLink)
                    .build();

            SmsSendResult result;
            try {
                result = smsService.sendDonationAlert(sendRequest);
            } catch (Exception ex) {
                log.error("Exception during SMS alert dispatch for recipient {}: {}", maskedPhone, ex.getMessage());
                result = SmsSendResult.failure("Provider dispatch error: " + ex.getMessage(), smsService.getConfiguredProvider().toUpperCase());
            }

            logEntry.setDeliveryStatus(result.getDeliveryStatus());
            logEntry.setProvider(result.getProviderName() != null ? result.getProviderName() : smsService.getConfiguredProvider().toUpperCase());
            logEntry.setProviderMessageId(result.getProviderMessageId());
            logEntry.setFailureReason(result.getFailureReason());
            if (result.isSuccess()) {
                logEntry.setSentAt(LocalDateTime.now());
            }

            smsNotificationLogRepository.save(logEntry);
        }

        // Also record an in-app notification for the NGO dashboard notification list
        try {
            notificationRepository.save(Notification.builder()
                    .recipient(ngoUser)
                    .donor(donation.getDonor())
                    .title("Nearby food donation available")
                    .message(donation.getFoodName() + " (" + donation.getQuantity() + " " +
                            donation.getQuantityUnit() + ") is available ~" + distanceKm + " km away.")
                    .distanceKm(distanceKm)
                    .build());
        } catch (Exception ex) {
            log.warn("Could not save secondary in-app notification: {}", ex.getMessage());
        }
    }
}
