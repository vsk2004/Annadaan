package com.fooddonation.backend.event;

public class DonationCreatedEvent {
    private final Long donationId;

    public DonationCreatedEvent(Long donationId) {
        this.donationId = donationId;
    }

    public Long getDonationId() {
        return donationId;
    }
}
