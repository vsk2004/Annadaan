package com.fooddonation.backend.event;

public class DonationAcceptedEvent {

    private final Long donationId;
    private final Long ngoProfileId;

    public DonationAcceptedEvent(Long donationId, Long ngoProfileId) {
        this.donationId = donationId;
        this.ngoProfileId = ngoProfileId;
    }

    public Long getDonationId() {
        return donationId;
    }

    public Long getNgoProfileId() {
        return ngoProfileId;
    }
}
