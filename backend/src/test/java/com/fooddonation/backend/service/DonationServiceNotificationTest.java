package com.fooddonation.backend.service;

import com.fooddonation.backend.dto.DonationRequest;
import com.fooddonation.backend.dto.DonationResponse;
import com.fooddonation.backend.entity.Donation;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import com.fooddonation.backend.entity.Donation.FoodCategory;
import com.fooddonation.backend.entity.Donation.QuantityUnit;
import com.fooddonation.backend.entity.NgoProfile;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import com.fooddonation.backend.entity.User;
import com.fooddonation.backend.entity.User.Role;
import com.fooddonation.backend.event.DonationCreatedEvent;
import com.fooddonation.backend.exception.ConflictException;
import com.fooddonation.backend.repository.DonationRepository;
import com.fooddonation.backend.repository.DonationStatusHistoryRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationServiceNotificationTest {

    @Mock private DonationRepository donationRepository;
    @Mock private DonationStatusHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private NgoProfileRepository ngoProfileRepository;
    @Mock private DonationEmailService donationEmailService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private DonationService donationService;

    private User donor;
    private DonationRequest validRequest;

    @BeforeEach
    void setUp() {
        donor = User.builder()
                .id(1L)
                .email("donor@example.com")
                .name("Donor Organization")
                .role(Role.DONOR)
                .build();

        validRequest = new DonationRequest();
        validRequest.setFoodName("Fresh Rice & Curry");
        validRequest.setCategory(FoodCategory.COOKED_MEALS);
        validRequest.setQuantity(20.0);
        validRequest.setQuantityUnit(QuantityUnit.MEALS);
        validRequest.setPreparationTime(LocalDateTime.now().minusHours(1));
        validRequest.setPickupWindowHours(3);
        validRequest.setPickupLatitude(19.0760);
        validRequest.setPickupLongitude(72.8777);
        validRequest.setPickupAddress("Bandra Kurla Complex, Mumbai");
        validRequest.setSafetyConfirmed(true);
    }

    @Test
    @DisplayName("Requirement 1: Valid donation persists in database and publishes DonationCreatedEvent")
    void testCreateValidDonationPublishesEvent() throws IOException {
        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donor));
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> {
            Donation d = invocation.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 101L);
            return d;
        });

        DonationResponse response = donationService.createDonation(validRequest, "donor@example.com", null);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("Fresh Rice & Curry", response.getFoodName());

        // Event published with the saved donation's ID
        ArgumentCaptor<DonationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(DonationCreatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertEquals(101L, eventCaptor.getValue().getDonationId());
    }

    @Test
    @DisplayName("Requirement 2: Invalid donation payload fails validation and does NOT publish event")
    void testInvalidDonationPayloadDoesNotPublishEvent() {
        // Preparation time in the future
        validRequest.setPreparationTime(LocalDateTime.now().plusHours(2));

        assertThrows(IllegalArgumentException.class, () ->
                donationService.createDonation(validRequest, "donor@example.com", null));

        verify(eventPublisher, never()).publishEvent(any());
        verify(donationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Requirement 3: Database failure on donation save throws error and does NOT publish event")
    void testDatabaseFailureDoesNotPublishEvent() {
        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donor));
        when(donationRepository.save(any(Donation.class))).thenThrow(new RuntimeException("DB Connection Timeout"));

        assertThrows(RuntimeException.class, () ->
                donationService.createDonation(validRequest, "donor@example.com", null));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Requirement 14: Atomic claim logic prevents second NGO from claiming an already accepted donation")
    void testAtomicClaimPreventsDoubleClaim() {
        User ngo1User = User.builder().id(201L).email("ngo1@example.com").role(Role.NGO).build();
        User ngo2User = User.builder().id(202L).email("ngo2@example.com").role(Role.NGO).build();

        NgoProfile ngo1Profile = NgoProfile.builder()
                .id(11L)
                .user(ngo1User)
                .organizationName("NGO One")
                .verificationStatus(VerificationStatus.VERIFIED)
                .latitude(19.0760)
                .longitude(72.8777)
                .build();

        NgoProfile ngo2Profile = NgoProfile.builder()
                .id(12L)
                .user(ngo2User)
                .organizationName("NGO Two")
                .verificationStatus(VerificationStatus.VERIFIED)
                .latitude(19.0760)
                .longitude(72.8777)
                .build();

        Donation donation = Donation.builder()
                .id(55L)
                .donor(donor)
                .foodName("Hot Meals")
                .status(DonationStatus.AVAILABLE)
                .pickupLatitude(19.0760)
                .pickupLongitude(72.8777)
                .pickupDeadline(LocalDateTime.now().plusHours(2))
                .build();

        when(ngoProfileRepository.findByUserEmail("ngo1@example.com")).thenReturn(Optional.of(ngo1Profile));
        when(donationRepository.findByIdWithLock(55L)).thenReturn(Optional.of(donation));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));

        // First NGO accepts donation successfully
        DonationResponse firstClaim = donationService.acceptDonation(55L, "ngo1@example.com", 10.0);
        assertNotNull(firstClaim);
        assertEquals(DonationStatus.ACCEPTED, donation.getStatus());

        // Second NGO tries to accept the now ACCEPTED donation
        when(ngoProfileRepository.findByUserEmail("ngo2@example.com")).thenReturn(Optional.of(ngo2Profile));
        when(donationRepository.findByIdWithLock(55L)).thenReturn(Optional.of(donation));

        assertThrows(ConflictException.class, () ->
                donationService.acceptDonation(55L, "ngo2@example.com", 10.0));
    }
}
