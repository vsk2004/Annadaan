package com.fooddonation.backend.sms;

public interface SmsProvider {
    String getName();
    boolean isAvailable();
    SmsSendResult sendDonationAlert(SmsSendRequest request);
}
