package com.fooddonation.backend.listener;

import com.fooddonation.backend.event.DonationAcceptedEvent;
import com.fooddonation.backend.service.DonationEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DonationEmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DonationEmailNotificationListener.class);

    @Autowired
    private DonationEmailService donationEmailService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDonationAccepted(DonationAcceptedEvent event) {
        if (event == null || event.getDonationId() == null) {
            log.warn("Received empty or invalid DonationAcceptedEvent; skipping email notification");
            return;
        }

        log.info("Processing post-commit email notification workflow for accepted donation #{}", event.getDonationId());
        try {
            donationEmailService.sendAcceptanceEmailNotification(event.getDonationId());
        } catch (Exception ex) {
            log.error("Unexpected error in post-commit email notification listener for donation #{}: {}",
                    event.getDonationId(), ex.getMessage(), ex);
        }
    }
}
