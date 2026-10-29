package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.PasswordResetToken;
import com.fooddonation.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_reset_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PasswordResetTokenRepositoryTest {

    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void lookupReturnsOnlyMatchingUnexpiredTokens() {
        User user = userRepository.save(User.builder()
                .name("Reset Test")
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .phone("1234567890")
                .role(User.Role.DONOR)
                .build());

        LocalDateTime now = LocalDateTime.now();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash("expired-token-hash")
                .expiresAt(now.minusSeconds(1))
                .build());
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash("valid-token-hash")
                .expiresAt(now.plusMinutes(5))
                .build());

        assertTrue(tokenRepository.findByTokenHashAndExpiresAtAfter("valid-token-hash", now).isPresent());
        assertFalse(tokenRepository.findByTokenHashAndExpiresAtAfter("expired-token-hash", now).isPresent());
        assertFalse(tokenRepository.findByTokenHashAndExpiresAtAfter("unknown-token-hash", now).isPresent());
    }
}