package com.fooddonation.backend.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final boolean smsEnabled;
    private final String configuredProvider;
    private final MockSmsProvider mockSmsProvider;
    private final TwilioSmsProvider twilioSmsProvider;
    private final Msg91SmsProvider msg91SmsProvider;

    public SmsService(
            @Value("${sms.enabled:${SMS_ENABLED:false}}") boolean smsEnabled,
            @Value("${sms.provider:${SMS_PROVIDER:mock}}") String configuredProvider,
            MockSmsProvider mockSmsProvider,
            TwilioSmsProvider twilioSmsProvider,
            @Autowired(required = false) Msg91SmsProvider msg91SmsProvider) {
        this.smsEnabled = smsEnabled;
        this.configuredProvider = configuredProvider != null ? configuredProvider.trim().toLowerCase() : "mock";
        this.mockSmsProvider = mockSmsProvider;
        this.twilioSmsProvider = twilioSmsProvider;
        this.msg91SmsProvider = msg91SmsProvider;

        log.info("SmsService initialized. Enabled: {}, Configured Provider: {}",
                this.smsEnabled, this.configuredProvider);
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public String getConfiguredProvider() {
        return configuredProvider;
    }

    public SmsSendResult sendDonationAlert(SmsSendRequest request) {
        if (!smsEnabled) {
            log.debug("SMS delivery is disabled (sms.enabled=false). Simulating via MockSmsProvider.");
            return mockSmsProvider.sendDonationAlert(request);
        }

        if ("twilio".equalsIgnoreCase(configuredProvider)) {
            if (!twilioSmsProvider.isAvailable()) {
                log.warn("Twilio requested but credentials are not configured. Falling back to MockSmsProvider.");
                return mockSmsProvider.sendDonationAlert(request);
            }
            return twilioSmsProvider.sendDonationAlert(request);
        }

        if ("msg91".equalsIgnoreCase(configuredProvider)) {
            if (msg91SmsProvider != null && msg91SmsProvider.isAvailable()) {
                return msg91SmsProvider.sendDonationAlert(request);
            }
            log.warn("MSG91 requested but credentials/provider not available. Falling back to MockSmsProvider.");
            return mockSmsProvider.sendDonationAlert(request);
        }

        return mockSmsProvider.sendDonationAlert(request);
    }
}
