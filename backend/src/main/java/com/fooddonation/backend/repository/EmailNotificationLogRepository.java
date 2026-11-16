package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.EmailNotificationLog;
import com.fooddonation.backend.entity.EmailNotificationLog.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailNotificationLogRepository extends JpaRepository<EmailNotificationLog, Long> {

    List<EmailNotificationLog> findByDonationIdOrderByCreatedAtDesc(Long donationId);

    Optional<EmailNotificationLog> findFirstByDonationIdOrderByCreatedAtDesc(Long donationId);

    boolean existsByDonationIdAndDeliveryStatus(Long donationId, DeliveryStatus deliveryStatus);

    List<EmailNotificationLog> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    boolean existsByDonationIdAndRecipientId(Long donationId, Long recipientId);

    boolean existsByDonationIdAndRecipientIdAndDeliveryStatus(Long donationId, Long recipientId, DeliveryStatus deliveryStatus);

    Optional<EmailNotificationLog> findFirstByDonationIdAndRecipientIdOrderByCreatedAtDesc(Long donationId, Long recipientId);
}
