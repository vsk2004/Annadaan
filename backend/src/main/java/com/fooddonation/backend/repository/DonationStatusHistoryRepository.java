package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.DonationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DonationStatusHistoryRepository extends JpaRepository<DonationStatusHistory, Long> {
    List<DonationStatusHistory> findByDonationIdOrderByChangedAtAsc(Long donationId);
}
