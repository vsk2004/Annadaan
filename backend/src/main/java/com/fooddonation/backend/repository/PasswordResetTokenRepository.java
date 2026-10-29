package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);
    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
    long deleteByExpiresAtBefore(LocalDateTime time);
}