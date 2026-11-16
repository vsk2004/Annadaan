package com.fooddonation.backend.controller;

import com.fooddonation.backend.dto.NotificationPreferenceRequest;
import com.fooddonation.backend.dto.NotificationPreferenceResponse;
import com.fooddonation.backend.dto.SmsNotificationLogResponse;
import com.fooddonation.backend.entity.Donation;
import com.fooddonation.backend.entity.NgoProfile;
import com.fooddonation.backend.entity.SmsNotificationLog;
import com.fooddonation.backend.entity.SmsNotificationLog.DeliveryStatus;
import com.fooddonation.backend.entity.User;
import com.fooddonation.backend.repository.NgoProfileRepository;
import com.fooddonation.backend.repository.SmsNotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NgoNotificationSecurityTest {

    @Mock private NgoProfileRepository ngoProfileRepository;
    @Mock private SmsNotificationLogRepository smsNotificationLogRepository;
    @Mock private UserDetails userDetails;

    @InjectMocks private NgoController ngoController;

    private User ngoUserA;
    private NgoProfile ngoProfileA;

    @BeforeEach
    void setUp() {
        ngoUserA = User.builder()
                .id(10L)
                .email("ngo-a@example.com")
                .name("NGO Alpha")
                .phone("9876543210")
                .build();

        ngoProfileA = NgoProfile.builder()
                .id(100L)
                .user(ngoUserA)
                .organizationName("NGO Alpha Org")
                .smsNotificationsEnabled(true)
                .build();
    }

    @Test
    @DisplayName("Requirement 15: NGO cannot access SMS notification logs of other NGOs")
    void testNgoCannotAccessOtherNgoLogs() {
        when(userDetails.getUsername()).thenReturn("ngo-a@example.com");
        when(ngoProfileRepository.findByUserEmailIgnoreCase("ngo-a@example.com")).thenReturn(Optional.of(ngoProfileA));

        Donation donation = Donation.builder().id(99L).foodName("Biryani").build();

        SmsNotificationLog logA = SmsNotificationLog.builder()
                .id(1L)
                .donation(donation)
                .recipient(ngoUserA)
                .phoneNumber("919876543210")
                .channel("SMS")
                .deliveryStatus(DeliveryStatus.SENT)
                .distanceKm(3.2)
                .createdAt(LocalDateTime.now())
                .build();

        // Repository must only be queried with NGO Alpha's user ID (10L)
        when(smsNotificationLogRepository.findByRecipientIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(logA));

        ResponseEntity<List<SmsNotificationLogResponse>> responseEntity = ngoController.smsNotifications(userDetails);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        List<SmsNotificationLogResponse> logs = responseEntity.getBody();
        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals(1L, logs.get(0).getId());

        // Verify repository was queried exclusively for recipient 10L, preventing data leakage
        verify(smsNotificationLogRepository).findByRecipientIdOrderByCreatedAtDesc(10L);
        verify(smsNotificationLogRepository, never()).findAll();
    }

    @Test
    @DisplayName("Requirement 16: API DTO responses do not leak MSG91 auth key, sender ID, or unmasked sensitive info")
    void testNoSensitiveCredentialLeakage() {
        when(userDetails.getUsername()).thenReturn("ngo-a@example.com");
        when(ngoProfileRepository.findByUserEmailIgnoreCase("ngo-a@example.com")).thenReturn(Optional.of(ngoProfileA));

        ResponseEntity<NotificationPreferenceResponse> prefResponse = ngoController.getNotificationPreferences(userDetails);
        assertNotNull(prefResponse.getBody());
        assertTrue(prefResponse.getBody().isSmsNotificationsEnabled());

        Donation donation = Donation.builder().id(99L).foodName("Bread").build();
        SmsNotificationLog logA = SmsNotificationLog.builder()
                .id(1L)
                .donation(donation)
                .recipient(ngoUserA)
                .phoneNumber("919876543210")
                .channel("SMS")
                .deliveryStatus(DeliveryStatus.SENT)
                .distanceKm(2.0)
                .createdAt(LocalDateTime.now())
                .build();

        when(smsNotificationLogRepository.findByRecipientIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(logA));

        ResponseEntity<List<SmsNotificationLogResponse>> logsResponse = ngoController.smsNotifications(userDetails);
        assertNotNull(logsResponse.getBody());
        SmsNotificationLogResponse logDto = logsResponse.getBody().get(0);

        // Verify recipient phone is not leaked on DTO response
        assertNull(getFieldValueQuietly(logDto, "phoneNumber"));
        assertNull(getFieldValueQuietly(logDto, "phone"));

        // Verify reflection / properties: no auth key, secret, or senderId exposed on DTO
        assertNull(getFieldValueQuietly(logDto, "authKey"));
        assertNull(getFieldValueQuietly(logDto, "senderId"));
        assertNull(getFieldValueQuietly(logDto, "accountSid"));
        assertNull(getFieldValueQuietly(logDto, "authToken"));
    }

    private Object getFieldValueQuietly(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException e) {
            return null; // Field does not exist, which is expected
        } catch (Exception e) {
            return "error";
        }
    }
}
