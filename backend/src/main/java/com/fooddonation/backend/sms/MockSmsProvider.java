package com.fooddonation.backend.sms;

import com.fooddonation.backend.util.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);
    public static final String PROVIDER_NAME = "MOCK";

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public SmsSendResult sendDonationAlert(SmsSendRequest request) {
        String masked = request.getMaskedPhone() != null
                ? request.getMaskedPhone()
                : PhoneUtils.maskPhone(request.getRecipientPhone());

        log.info("[MOCK SMS] Simulated alert sent to {} for donation #{}: {} ({} {}) ~{} km away. Pickup by: {}. Link: {}",
                masked,
                request.getDonationId(),
                request.getFoodName(),
                request.getQuantity(),
                request.getUnit(),
                request.getDistanceKm(),
                request.getPickupDeadline(),
                request.getAppLink());

        String mockId = "mock-" + UUID.randomUUID();
        return SmsSendResult.success(mockId, PROVIDER_NAME);
    }
}
