package com.fooddonation.backend.sms;

import com.fooddonation.backend.entity.SmsNotificationLog.DeliveryStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SmsSendResult {
    private boolean success;
    private String providerMessageId;
    private String failureReason;
    private String providerName;
    private DeliveryStatus deliveryStatus;

    public static SmsSendResult success(String providerMessageId, String providerName) {
        return SmsSendResult.builder()
                .success(true)
                .providerMessageId(providerMessageId)
                .providerName(providerName)
                .deliveryStatus(DeliveryStatus.SENT)
                .build();
    }

    public static SmsSendResult failure(String failureReason, String providerName) {
        return SmsSendResult.builder()
                .success(false)
                .failureReason(failureReason)
                .providerName(providerName)
                .deliveryStatus(DeliveryStatus.FAILED)
                .build();
    }
}
