package com.fooddonation.backend.dto;

import lombok.Data;

@Data
public class NotificationPreferenceRequest {
    private Boolean smsNotificationsEnabled;
}
