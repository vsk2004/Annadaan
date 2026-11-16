package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.SmsNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmsNotificationLogRepository extends JpaRepository<SmsNotificationLog, Long> {
    boolean existsByDonationIdAndRecipientId(Long donationId, Long recipientId);
    Optional<SmsNotificationLog> findByDonationIdAndRecipientId(Long donationId, Long recipientId);
    List<SmsNotificationLog> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    List<SmsNotificationLog> findByDonationId(Long donationId);
}
