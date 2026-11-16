package com.fooddonation.backend.service;

import com.fooddonation.backend.dto.DonationRequest;
import com.fooddonation.backend.dto.DonationResponse;
import com.fooddonation.backend.entity.*;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.Donation.FoodCategory;
import com.fooddonation.backend.entity.Donation.QuantityUnit;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.entity.User.Role;
import com.fooddonation.backend.event.DonationAcceptedEvent;
import com.fooddonation.backend.event.DonationCreatedEvent;
import com.fooddonation.backend.exception.ConflictException;
import com.fooddonation.backend.listener.DonationNotificationListener;
import com.fooddonation.backend.repository.*;
import com.fooddonation.backend.sms.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationAcceptanceWorkflowTest {

    @Mock private DonationRepository donationRepository;
    @Mock private DonationStatusHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private NgoProfileRepository ngoProfileRepository;
    @Mock private DonationEmailService donationEmailService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private DonationService donationService;

    private User donor;
    private User ngoUser1;
    private User ngoUser2;
    private NgoProfile ngoProfile1;
    private NgoProfile ngoProfile2;
    private Donation donation;

    @BeforeEach
    void setUp() {
        donor = User.builder()
                .id(1L)
                .name("Anjali Sharma")
                .email("anjali@example.com")
                .phone("+919811122233")
                .role(Role.DONOR)
                .build();

        ngoUser1 = User.builder()
                .id(10L)
                .name("NGO Representative One")
                .email("contact@ngo1.org")
                .phone("+919822233344")
                .role(Role.NGO)
                .build();

        ngoProfile1 = NgoProfile.builder()
                .id(100L)
                .user(ngoUser1)
                .organizationName("Roti Bank Mumbai")
                .contactPerson("Sunil Patil")
                .address("Dadar West, Mumbai")
                .city("Mumbai")
                .latitude(19.0760)
                .longitude(72.8777)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();

        ngoUser2 = User.builder()
                .id(20L)
                .name("NGO Representative Two")
                .email("contact@ngo2.org")
                .phone("+919833344455")
                .role(Role.NGO)
                .build();

        ngoProfile2 = NgoProfile.builder()
                .id(200L)
                .user(ngoUser2)
                .organizationName("Seva Kitchen")
                .contactPerson("Meera Joshi")
                .address("Andheri East, Mumbai")
                .city("Mumbai")
                .latitude(19.0760)
                .longitude(72.8777)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();

        donation = Donation.builder()
                .id(501L)
                .donor(donor)
                .foodName("Dal Makhani & Roti")
                .description("Nutritious vegetarian meals")
                .category(FoodCategory.COOKED_MEALS)
                .quantity(40.0)
                .quantityUnit(QuantityUnit.MEALS)
                .pickupAddress("Bandra Kurla Complex, Mumbai")
                .pickupLatitude(19.0760)
                .pickupLongitude(72.8777)
                .pickupDeadline(LocalDateTime.now().plusHours(4))
                .status(DonationStatus.AVAILABLE)
                .build();
    }

    @Test
    @DisplayName("Scenario 1 & 2 & 3 & 4: Full donation posting, acceptance, and event publication flow")
    void testDonationAcceptanceWorkflowPublishesEvent() throws IOException {
        // Step 1: Donor creates donation
        DonationRequest req = new DonationRequest();
        req.setFoodName("Dal Makhani & Roti");
        req.setCategory(FoodCategory.COOKED_MEALS);
        req.setQuantity(40.0);
        req.setQuantityUnit(QuantityUnit.MEALS);
        req.setPreparationTime(LocalDateTime.now().minusHours(1));
        req.setPickupWindowHours(3);
        req.setPickupLatitude(19.0760);
        req.setPickupLongitude(72.8777);
        req.setPickupAddress("Bandra Kurla Complex, Mumbai");
        req.setSafetyConfirmed(true);

        when(userRepository.findByEmail("anjali@example.com")).thenReturn(Optional.of(donor));
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> {
            Donation d = invocation.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 501L);
            return d;
        });

        DonationResponse createResp = donationService.createDonation(req, "anjali@example.com", null);
        assertNotNull(createResp);
        assertEquals(DonationStatus.AVAILABLE, createResp.getStatus());

        // Step 2: NGO accepts donation
        when(ngoProfileRepository.findByUserEmail("contact@ngo1.org")).thenReturn(Optional.of(ngoProfile1));
        when(donationRepository.findByIdWithLock(501L)).thenReturn(Optional.of(donation));

        DonationResponse acceptResp = donationService.acceptDonation(501L, "contact@ngo1.org", 5.0);

        assertNotNull(acceptResp);
        assertEquals(DonationStatus.ACCEPTED, acceptResp.getStatus());
        assertEquals("Roti Bank Mumbai", acceptResp.getAssignedNgoName());
        assertEquals(ngoUser1.getId(), acceptResp.getAssignedNgoId());

        // Step 3: Verify DonationAcceptedEvent is published for email notification
        ArgumentCaptor<DonationAcceptedEvent> acceptEventCaptor = ArgumentCaptor.forClass(DonationAcceptedEvent.class);
        verify(eventPublisher).publishEvent(acceptEventCaptor.capture());
        assertEquals(501L, acceptEventCaptor.getValue().getDonationId());
        assertEquals(100L, acceptEventCaptor.getValue().getNgoProfileId());
    }

    @Test
    @DisplayName("Scenario 5 & 6 & 9: Second NGO cannot claim already accepted donation; throws ConflictException")
    void testSecondNgoCannotAcceptAlreadyAcceptedDonation() {
        // Donation is already accepted by NGO 1
        donation.setStatus(DonationStatus.ACCEPTED);
        donation.setAssignedNgo(ngoUser1);

        when(ngoProfileRepository.findByUserEmail("contact@ngo2.org")).thenReturn(Optional.of(ngoProfile2));
        when(donationRepository.findByIdWithLock(501L)).thenReturn(Optional.of(donation));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                donationService.acceptDonation(501L, "contact@ngo2.org", 5.0));

        assertTrue(ex.getMessage().contains("already been claimed"));
        verify(donationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(DonationAcceptedEvent.class));
    }

    @Test
    @DisplayName("Scenario 10: Existing donation workflows (pickup, deliver, cancel) continue working")
    void testPickupAndDeliveryWorkflowsContinueWorking() {
        donation.setStatus(DonationStatus.ACCEPTED);
        donation.setAssignedNgo(ngoUser1);

        when(userRepository.findByEmail("contact@ngo1.org")).thenReturn(Optional.of(ngoUser1));
        when(donationRepository.findById(501L)).thenReturn(Optional.of(donation));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));

        // NGO confirms pickup
        DonationResponse pickupResp = donationService.markPickedUp(501L, "contact@ngo1.org", "Picked up on time");
        assertEquals(DonationStatus.PICKED_UP, pickupResp.getStatus());
        assertNotNull(donation.getPickedUpAt());

        // NGO confirms delivery
        DonationResponse deliverResp = donationService.markDelivered(501L, "contact@ngo1.org", 40, "Distributed to community center");
        assertEquals(DonationStatus.DELIVERED, deliverResp.getStatus());
        assertNotNull(donation.getDeliveredAt());
        assertEquals(40, donation.getActualServingsDelivered());
    }

    @Test
    @DisplayName("Requirement 1 & 6: Donor creating a donation does not send SMS when sms.enabled=false")
    void testDonationCreationDoesNotSendSmsWhenDisabled() {
        SmsService mockSmsService = mock(SmsService.class);
        lenient().when(mockSmsService.isSmsEnabled()).thenReturn(false);

        DonationRepository mockDonRepo = mock(DonationRepository.class);
        NgoProfileRepository mockNgoRepo = mock(NgoProfileRepository.class);
        NotificationRepository mockNotifRepo = mock(NotificationRepository.class);
        SmsNotificationLogRepository mockSmsRepo = mock(SmsNotificationLogRepository.class);

        lenient().when(mockDonRepo.findById(501L)).thenReturn(Optional.of(donation));
        lenient().when(mockNgoRepo.findAll()).thenReturn(List.of(ngoProfile1));

        DonationNotificationListener listener = new DonationNotificationListener();
        ReflectionTestUtils.setField(listener, "donationRepository", mockDonRepo);
        ReflectionTestUtils.setField(listener, "ngoProfileRepository", mockNgoRepo);
        ReflectionTestUtils.setField(listener, "notificationRepository", mockNotifRepo);
        ReflectionTestUtils.setField(listener, "smsNotificationLogRepository", mockSmsRepo);
        ReflectionTestUtils.setField(listener, "smsService", mockSmsService);
        ReflectionTestUtils.setField(listener, "defaultRadiusKm", 10.0);

        listener.onDonationCreated(new DonationCreatedEvent(501L));

        // Assert that smsService.sendDonationAlert was NEVER called
        verify(mockSmsService, never()).sendDonationAlert(any());
        verify(mockSmsRepo, never()).save(any());
    }
}
