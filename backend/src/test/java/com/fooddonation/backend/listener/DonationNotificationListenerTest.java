package com.fooddonation.backend.listener;

import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.Donation.QuantityUnit;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.entity.SmsNotificationLog.DeliveryStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationNotificationListenerTest {

    @Mock private DonationRepository donationRepository;
    @Mock private NgoProfileRepository ngoProfileRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SmsNotificationLogRepository smsNotificationLogRepository;
    @Mock private SmsService smsService;

    @InjectMocks private DonationNotificationListener listener;

    private Donation testDonation;
    private User donorUser;

    @BeforeEach
    void setUp() {
        lenient().when(smsService.isSmsEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(listener, "appBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(listener, "defaultRadiusKm", 10.0);
        ReflectionTestUtils.setField(listener, "msg91TemplateId", "flow_test_tpl_123");

        donorUser = User.builder().id(100L).name("Donor Dan").email("donor@example.com").build();

        // Donation at Mumbai coordinates: 19.0760, 72.8777
        testDonation = Donation.builder()
                .id(1L)
                .donor(donorUser)
                .foodName("Surplus Veg Meals")
                .quantity(30.0)
                .quantityUnit(QuantityUnit.MEALS)
                .status(DonationStatus.AVAILABLE)
                .pickupLatitude(19.0760)
                .pickupLongitude(72.8777)
                .pickupDeadline(LocalDateTime.now().plusHours(4))
                .build();
    }

    private NgoProfile createNgo(Long userId, String email, String phone, VerificationStatus vStatus,
                                 AccountStatus accStatus, boolean smsOptIn, Double lat, Double lng) {
        User user = User.builder()
                .id(userId)
                .name("NGO " + userId)
                .email(email)
                .phone(phone)
                .role(Role.NGO)
                .accountStatus(accStatus)
                .build();

        return NgoProfile.builder()
                .id(userId * 10)
                .user(user)
                .organizationName("Charity " + userId)
                .verificationStatus(vStatus)
                .smsNotificationsEnabled(smsOptIn)
                .latitude(lat)
                .longitude(lng)
                .build();
    }

    @Test
    @DisplayName("Requirements 1 & 4: Only eligible NGOs within radius (2 km, 7 km) are notified; 15 km is excluded")
    void testRadiusFilteringScenario() {
        // Donation at: 19.0760, 72.8777
        // NGO 1 at ~2.0 km: 19.0900, 72.8850
        NgoProfile ngo2km = createNgo(1L, "ngo1@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0900, 72.8850);

        // NGO 2 at ~7.0 km: 19.1300, 72.9100
        NgoProfile ngo7km = createNgo(2L, "ngo2@example.com", "9876543211",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.1300, 72.9100);

        // NGO 3 at ~15.0 km: 19.2000, 72.9500 (outside 10 km)
        NgoProfile ngo15km = createNgo(3L, "ngo3@example.com", "9876543212",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.2000, 72.9500);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(ngo2km, ngo7km, ngo15km));
        when(smsNotificationLogRepository.existsByDonationIdAndRecipientId(anyLong(), anyLong())).thenReturn(false);
        when(smsService.getConfiguredProvider()).thenReturn("MOCK");
        when(smsService.sendDonationAlert(any())).thenReturn(SmsSendResult.success("mock-123", "MOCK"));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        // Exactly 2 SMS requests sent (NGO 1 and NGO 2), NGO 3 excluded
        verify(smsService, times(2)).sendDonationAlert(any());
        verify(smsNotificationLogRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Requirement 5: Unverified NGOs (PENDING or REJECTED) are excluded from notifications")
    void testUnverifiedNgoExcluded() {
        NgoProfile pendingNgo = createNgo(1L, "p@example.com", "9876543210",
                VerificationStatus.PENDING, AccountStatus.ACTIVE, true, 19.0770, 72.8780);
        NgoProfile rejectedNgo = createNgo(2L, "r@example.com", "9876543211",
                VerificationStatus.REJECTED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(pendingNgo, rejectedNgo));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("Requirement 6: Inactive or suspended NGO accounts are excluded")
    void testInactiveNgoExcluded() {
        NgoProfile suspendedNgo = createNgo(1L, "s@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.SUSPENDED, true, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(suspendedNgo));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("Requirement 7: NGOs without a valid registered phone number are excluded")
    void testNgoWithoutValidPhoneExcluded() {
        NgoProfile noPhoneNgo = createNgo(1L, "n1@example.com", null,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);
        NgoProfile invalidPhoneNgo = createNgo(2L, "n2@example.com", "12345",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(noPhoneNgo, invalidPhoneNgo));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("Requirement 8: NGOs with SMS preference disabled (opted out) are excluded")
    void testNgoOptedOutExcluded() {
        NgoProfile optOutNgo = createNgo(1L, "optout@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, false, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(optOutNgo));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("Requirement 9: Valid donation with zero eligible NGOs completes cleanly without error")
    void testZeroEligibleNgos() {
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of());

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, never()).sendDonationAlert(any());
        verify(smsNotificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Requirement 10: Provider failure records FAILED status and does not throw exception")
    void testProviderFailureHandling() {
        NgoProfile eligibleNgo = createNgo(1L, "ngo@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(eligibleNgo));
        when(smsNotificationLogRepository.existsByDonationIdAndRecipientId(1L, 1L)).thenReturn(false);
        when(smsService.getConfiguredProvider()).thenReturn("MSG91");
        when(smsService.sendDonationAlert(any())).thenReturn(SmsSendResult.failure("MSG91 timeout", "MSG91"));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        ArgumentCaptor<SmsNotificationLog> logCaptor = ArgumentCaptor.forClass(SmsNotificationLog.class);
        verify(smsNotificationLogRepository).save(logCaptor.capture());

        SmsNotificationLog savedLog = logCaptor.getValue();
        assertEquals(DeliveryStatus.FAILED, savedLog.getDeliveryStatus());
        assertEquals("MSG91 timeout", savedLog.getFailureReason());
    }

    @Test
    @DisplayName("Requirement 11: Partial failure for NGO 1 does not stop sending to NGO 2")
    void testPartialProviderFailureIsolation() {
        NgoProfile ngo1 = createNgo(1L, "ngo1@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);
        NgoProfile ngo2 = createNgo(2L, "ngo2@example.com", "9876543211",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0780, 72.8790);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(ngo1, ngo2));
        when(smsNotificationLogRepository.existsByDonationIdAndRecipientId(anyLong(), anyLong())).thenReturn(false);
        when(smsService.getConfiguredProvider()).thenReturn("MOCK");

        // Fail for NGO 1, succeed for NGO 2
        when(smsService.sendDonationAlert(any()))
                .thenReturn(SmsSendResult.failure("Simulated network blip", "MOCK"))
                .thenReturn(SmsSendResult.success("mock-success-2", "MOCK"));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, times(2)).sendDonationAlert(any());
        verify(smsNotificationLogRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Requirement 12: Duplicate notification event skips already notified recipients (Idempotency)")
    void testDuplicateEventIdempotency() {
        NgoProfile eligibleNgo = createNgo(1L, "ngo@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(eligibleNgo));
        // Already logged
        when(smsNotificationLogRepository.existsByDonationIdAndRecipientId(1L, 1L)).thenReturn(true);

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        verify(smsService, never()).sendDonationAlert(any());
        verify(smsNotificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Requirement 13: Claimed or expired donation cancels alerts")
    void testClaimedOrExpiredDonationCancelsAlerts() {
        // Claimed donation
        testDonation.setStatus(DonationStatus.ACCEPTED);
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));

        listener.onDonationCreated(new DonationCreatedEvent(1L));
        verify(smsService, never()).sendDonationAlert(any());

        // Expired donation
        testDonation.setStatus(DonationStatus.AVAILABLE);
        testDonation.setPickupDeadline(LocalDateTime.now().minusMinutes(10));
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));

        listener.onDonationCreated(new DonationCreatedEvent(1L));
        verify(smsService, never()).sendDonationAlert(any());
    }

    @Test
    @DisplayName("Requirement 18: Notification status is saved as SENT upon dispatch, never DELIVERED")
    void testStatusIsSentNotDelivered() {
        NgoProfile eligibleNgo = createNgo(1L, "ngo@example.com", "9876543210",
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE, true, 19.0770, 72.8780);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(ngoProfileRepository.findAll()).thenReturn(List.of(eligibleNgo));
        when(smsNotificationLogRepository.existsByDonationIdAndRecipientId(1L, 1L)).thenReturn(false);
        when(smsService.getConfiguredProvider()).thenReturn("MOCK");
        when(smsService.sendDonationAlert(any())).thenReturn(SmsSendResult.success("mock-msg-99", "MOCK"));

        listener.onDonationCreated(new DonationCreatedEvent(1L));

        ArgumentCaptor<SmsNotificationLog> logCaptor = ArgumentCaptor.forClass(SmsNotificationLog.class);
        verify(smsNotificationLogRepository).save(logCaptor.capture());

        SmsNotificationLog savedLog = logCaptor.getValue();
        assertEquals(DeliveryStatus.SENT, savedLog.getDeliveryStatus());
        assertNotEquals(DeliveryStatus.DELIVERED, savedLog.getDeliveryStatus());
    }
}
