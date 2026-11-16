package com.fooddonation.backend.service;

import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DietaryType;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.Donation.FoodCategory;
import com.fooddonation.backend.entity.Donation.QuantityUnit;
import com.fooddonation.backend.entity.EmailNotificationLog.DeliveryStatus;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.entity.User.Role;
import com.fooddonation.backend.exception.BrevoEmailDeliveryException;
import com.fooddonation.backend.exception.ForbiddenException;
import com.fooddonation.backend.repository.DonationRepository;
import com.fooddonation.backend.repository.EmailNotificationLogRepository;
import com.fooddonation.backend.repository.NgoProfileRepository;
import com.fooddonation.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationEmailServiceTest {

    @Mock private DonationRepository donationRepository;
    @Mock private NgoProfileRepository ngoProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailNotificationLogRepository emailNotificationLogRepository;
    @Mock private BrevoTransactionalEmailSender emailSender;

    @InjectMocks private DonationEmailService donationEmailService;

    private User donor;
    private User ngoUser;
    private NgoProfile ngoProfile;
    private Donation donation;

    @BeforeEach
    void setUp() {
        donor = User.builder()
                .id(1L)
                .name("Alice Donor")
                .email("alice.donor@example.com")
                .phone("+919876543210")
                .role(Role.DONOR)
                .build();

        ngoUser = User.builder()
                .id(2L)
                .name("Ravi Kumar")
                .email("ravi.ngo@feedingindia.org")
                .phone("+919123456789")
                .role(Role.NGO)
                .build();

        ngoProfile = NgoProfile.builder()
                .id(20L)
                .user(ngoUser)
                .organizationName("Feeding India NGO")
                .contactPerson("Ravi Kumar")
                .address("Plot 42, Green Park")
                .city("Mumbai")
                .state("Maharashtra")
                .postalCode("400001")
                .latitude(19.0760)
                .longitude(72.8777)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();

        donation = Donation.builder()
                .id(101L)
                .donor(donor)
                .assignedNgo(ngoUser)
                .foodName("Fresh Rice & Veg Curry")
                .description("Packed meal boxes prepared 2 hours ago")
                .category(FoodCategory.COOKED_MEALS)
                .quantity(50.0)
                .quantityUnit(QuantityUnit.MEALS)
                .dietaryType(DietaryType.VEGETARIAN)
                .pickupAddress("Bandra West, Mumbai 400050")
                .pickupLatitude(19.0596)
                .pickupLongitude(72.8295)
                .pickupDeadline(LocalDateTime.now().plusHours(3))
                .status(DonationStatus.ACCEPTED)
                .acceptedAt(LocalDateTime.of(2026, 11, 15, 14, 30))
                .build();
    }

    @Test
    @DisplayName("Requirement 2 & 3: Email contains all required food, NGO, and acceptance details in text and HTML")
    void testEmailContentContainsAllRequiredFields() {
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));
        when(emailNotificationLogRepository.existsByDonationIdAndDeliveryStatus(101L, DeliveryStatus.SENT)).thenReturn(false);
        when(emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.empty());
        when(ngoProfileRepository.findByUserEmail("ravi.ngo@feedingindia.org")).thenReturn(Optional.of(ngoProfile));
        when(emailNotificationLogRepository.save(any(EmailNotificationLog.class))).thenAnswer(i -> i.getArgument(0));
        when(emailSender.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("brevo-msg-12345");

        EmailNotificationLog result = donationEmailService.sendAcceptanceEmailNotification(101L);

        assertNotNull(result);
        assertEquals(DeliveryStatus.SENT, result.getDeliveryStatus());

        ArgumentCaptor<String> recipientCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailSender).sendEmail(
                recipientCaptor.capture(),
                nameCaptor.capture(),
                subjectCaptor.capture(),
                htmlCaptor.capture(),
                textCaptor.capture()
        );

        // Subject check
        assertEquals("Your Annadaan Donation Has Been Accepted!", subjectCaptor.getValue());

        // Recipient is donor, NOT NGO
        assertEquals("alice.donor@example.com", recipientCaptor.getValue());
        assertNotEquals("ravi.ngo@feedingindia.org", recipientCaptor.getValue());
        assertEquals("Alice Donor", nameCaptor.getValue());

        // Verify text content contains all required fields
        String text = textCaptor.getValue();
        assertTrue(text.contains("Dear Alice Donor,"), "Should address donor");
        assertTrue(text.contains("Great news! Your food donation has been successfully accepted"), "Should contain intro");
        assertTrue(text.contains("Fresh Rice & Veg Curry"), "Should contain food name");
        assertTrue(text.contains("Packed meal boxes prepared 2 hours ago"), "Should contain description");
        assertTrue(text.contains("50.0 MEALS"), "Should contain quantity");
        assertTrue(text.contains("Cooked Meals"), "Should contain formatted category");
        assertTrue(text.contains("Bandra West, Mumbai 400050"), "Should contain pickup location");
        assertTrue(text.contains("#101"), "Should contain donation ID");
        assertTrue(text.contains("Feeding India NGO"), "Should contain NGO name");
        assertTrue(text.contains("Ravi Kumar"), "Should contain NGO representative");
        assertTrue(text.contains("ravi.ngo@feedingindia.org"), "Should contain NGO email");
        assertTrue(text.contains("+919123456789"), "Should contain NGO phone");
        assertTrue(text.contains("Plot 42, Green Park, Mumbai, Maharashtra - 400001"), "Should contain NGO address");
        assertTrue(text.contains("19.0760, 72.8777"), "Should contain NGO coordinates");
        assertTrue(text.contains("15 November 2026"), "Should contain accepted date");
        assertTrue(text.contains("02:30 pm"), "Should contain accepted time");
        assertTrue(text.contains("ACCEPTED"), "Should contain status");
        assertTrue(text.contains("Thank you for supporting Annadaan and helping reduce food waste. Please coordinate with the NGO using the contact details above to arrange the food pickup."), "Should contain coordination note");
        assertTrue(text.contains("Regards,\nTeam Annadaan"), "Should contain sign-off");

        // Verify HTML content has green theme, branding, and details
        String html = htmlCaptor.getValue();
        assertTrue(html.contains("Annadaan!"), "HTML must contain branding");
        assertTrue(html.contains("#2d6a4f") || html.contains("#1b4332"), "HTML must use green theme");
        assertTrue(html.contains("Fresh Rice &amp; Veg Curry") || html.contains("Fresh Rice & Veg Curry"), "HTML must contain food name");
        assertTrue(html.contains("Feeding India NGO"), "HTML must contain NGO name");
        assertTrue(html.contains("ravi.ngo@feedingindia.org"), "HTML must contain NGO email");
        assertTrue(html.contains("+919123456789"), "HTML must contain NGO phone");
        assertTrue(html.contains("Thank you for supporting Annadaan and helping reduce food waste"), "HTML must contain closing message");
    }

    @Test
    @DisplayName("Requirement 4: Notification is sent to original donor, not to NGO")
    void testEmailSentToDonorNotToNgo() {
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));
        when(emailNotificationLogRepository.existsByDonationIdAndDeliveryStatus(101L, DeliveryStatus.SENT)).thenReturn(false);
        when(emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.empty());
        when(ngoProfileRepository.findByUserEmail(anyString())).thenReturn(Optional.of(ngoProfile));
        when(emailNotificationLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        donationEmailService.sendAcceptanceEmailNotification(101L);

        ArgumentCaptor<String> recipientCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendEmail(recipientCaptor.capture(), any(), any(), any(), any());

        assertEquals("alice.donor@example.com", recipientCaptor.getValue());
        assertNotEquals("ravi.ngo@feedingindia.org", recipientCaptor.getValue());
    }

    @Test
    @DisplayName("Requirement 4 & Scenario 9: Duplicate email is prevented if acceptance was already notified")
    void testDuplicateAcceptanceEmailPreventedByIdempotency() {
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));
        when(emailNotificationLogRepository.existsByDonationIdAndDeliveryStatus(101L, DeliveryStatus.SENT)).thenReturn(true);
        EmailNotificationLog existingLog = EmailNotificationLog.builder()
                .donation(donation)
                .recipient(donor)
                .recipientEmail("alice.donor@example.com")
                .subject("Your Annadaan Donation Has Been Accepted!")
                .deliveryStatus(DeliveryStatus.SENT)
                .build();
        when(emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.of(existingLog));

        EmailNotificationLog result = donationEmailService.sendAcceptanceEmailNotification(101L);

        assertNotNull(result);
        assertEquals(DeliveryStatus.SENT, result.getDeliveryStatus());
        verify(emailSender, never()).sendEmail(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 7: Donor email address missing or invalid records FAILED log without throwing exception")
    void testMissingOrInvalidDonorEmailFailsGracefully() {
        donor.setEmail("invalid-email-format");
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));
        when(emailNotificationLogRepository.existsByDonationIdAndDeliveryStatus(101L, DeliveryStatus.SENT)).thenReturn(false);
        when(emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.empty());
        when(emailNotificationLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EmailNotificationLog result = donationEmailService.sendAcceptanceEmailNotification(101L);

        assertNotNull(result);
        assertEquals(DeliveryStatus.FAILED, result.getDeliveryStatus());
        assertTrue(result.getFailureReason().contains("invalid"));
        verify(emailSender, never()).sendEmail(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 8: Email provider failure is logged and recorded as FAILED without throwing runtime error")
    void testBrevoProviderFailureLoggedAndHandledGracefully() {
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));
        when(emailNotificationLogRepository.existsByDonationIdAndDeliveryStatus(101L, DeliveryStatus.SENT)).thenReturn(false);
        when(emailNotificationLogRepository.findFirstByDonationIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.empty());
        when(ngoProfileRepository.findByUserEmail(anyString())).thenReturn(Optional.of(ngoProfile));
        when(emailNotificationLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(emailSender.sendEmail(any(), any(), any(), any(), any()))
                .thenThrow(new BrevoEmailDeliveryException("Brevo API error (401): Unauthorized"));

        EmailNotificationLog result = donationEmailService.sendAcceptanceEmailNotification(101L);

        assertNotNull(result);
        assertEquals(DeliveryStatus.FAILED, result.getDeliveryStatus());
        assertTrue(result.getFailureReason().contains("Unauthorized"));
    }

    @Test
    @DisplayName("Requirement 4: Retry mechanism allows re-sending failed notification without re-accepting")
    void testRetryFailedAcceptanceEmail() {
        when(userRepository.findByEmail("alice.donor@example.com")).thenReturn(Optional.of(donor));
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));
        when(ngoProfileRepository.findByUserEmail(anyString())).thenReturn(Optional.of(ngoProfile));
        when(emailNotificationLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(emailSender.sendEmail(any(), any(), any(), any(), any())).thenReturn("retry-msg-789");

        EmailNotificationLog result = donationEmailService.retryAcceptanceEmail(101L, "alice.donor@example.com");

        assertNotNull(result);
        assertEquals(DeliveryStatus.SENT, result.getDeliveryStatus());
        assertEquals("retry-msg-789", result.getProviderMessageId());
        verify(emailSender, times(1)).sendEmail(eq("alice.donor@example.com"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Security: Unauthorized user cannot trigger notification retry")
    void testUnauthorizedUserCannotRetryEmail() {
        User outsider = User.builder().id(999L).email("stranger@example.com").role(Role.DONOR).build();
        when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(outsider));
        when(donationRepository.findById(101L)).thenReturn(Optional.of(donation));

        assertThrows(ForbiddenException.class, () ->
                donationEmailService.retryAcceptanceEmail(101L, "stranger@example.com"));

        verify(emailSender, never()).sendEmail(any(), any(), any(), any(), any());
    }
}
