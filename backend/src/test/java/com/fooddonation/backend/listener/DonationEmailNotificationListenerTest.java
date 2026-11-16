package com.fooddonation.backend.listener;

import com.fooddonation.backend.event.DonationAcceptedEvent;
import com.fooddonation.backend.event.DonationCreatedEvent;
import com.fooddonation.backend.service.DonationEmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationEmailNotificationListenerTest {

    @Mock
    private DonationEmailService donationEmailService;

    @InjectMocks
    private DonationEmailNotificationListener listener;

    @Test
    @DisplayName("DonationCreatedEvent triggers nearby NGO email notification workflow")
    void testOnDonationCreated_triggersWorkflow() {
        listener.onDonationCreated(new DonationCreatedEvent(101L));
        verify(donationEmailService, times(1)).sendNearbyDonationAlertEmails(101L);
    }

    @Test
    @DisplayName("DonationAcceptedEvent triggers donor acceptance email notification workflow")
    void testOnDonationAccepted_triggersWorkflow() {
        listener.onDonationAccepted(new DonationAcceptedEvent(102L, 50L));
        verify(donationEmailService, times(1)).sendAcceptanceEmailNotification(102L);
    }

    @Test
    @DisplayName("Null or invalid events are ignored gracefully without exceptions")
    void testNullOrInvalidEvents() {
        listener.onDonationCreated(null);
        listener.onDonationCreated(new DonationCreatedEvent(null));
        verify(donationEmailService, never()).sendNearbyDonationAlertEmails(any());

        listener.onDonationAccepted(null);
        listener.onDonationAccepted(new DonationAcceptedEvent(null, null));
        verify(donationEmailService, never()).sendAcceptanceEmailNotification(any());
    }

    @Test
    @DisplayName("Exceptions in email service are caught and logged without propagating")
    void testServiceExceptionsCaught() {
        doThrow(new RuntimeException("Simulated email service error"))
                .when(donationEmailService).sendNearbyDonationAlertEmails(103L);

        // Should not throw
        listener.onDonationCreated(new DonationCreatedEvent(103L));
        verify(donationEmailService, times(1)).sendNearbyDonationAlertEmails(103L);
    }
}
