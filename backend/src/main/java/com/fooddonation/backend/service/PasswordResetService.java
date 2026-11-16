package com.fooddonation.backend.service;

import com.fooddonation.backend.dto.ResetPasswordRequest;
import com.fooddonation.backend.entity.PasswordResetToken;
import com.fooddonation.backend.entity.User;
import com.fooddonation.backend.exception.BrevoEmailDeliveryException;
import com.fooddonation.backend.repository.PasswordResetTokenRepository;
import com.fooddonation.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_LIFETIME_MINUTES = 30;
    private static final int REQUEST_COOLDOWN_SECONDS = 60;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BrevoTransactionalEmailSender emailSender;

    @Value("${app.frontend.url}") private String frontendUrl;
    @Value("${app.mail.from:projectannadaan@gmail.com}") private String senderAddress;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void sendResetLink(String email) {
        LocalDateTime now = LocalDateTime.now();
        resetTokenRepository.deleteByExpiresAtBefore(now);

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElse(null);
        if (user == null) {
            logger.info("Password reset requested for non-registered email: {}", email);
            return;
        }

        boolean recentlyRequested = resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(token -> token.getCreatedAt().isAfter(now.minusSeconds(REQUEST_COOLDOWN_SECONDS)))
                .orElse(false);
        if (recentlyRequested) {
            logger.info("Password reset request throttled for user {} (cooldown {}s)", user.getEmail(), REQUEST_COOLDOWN_SECONDS);
            return;
        }

        resetTokenRepository.deleteByUserId(user.getId());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken resetToken = resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plusMinutes(TOKEN_LIFETIME_MINUTES))
                .build());

        String resetUrl = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        logger.info("Password reset link generated for [{}]: {}", user.getEmail(), resetUrl);
        try {
            emailSender.sendResetEmail(user.getEmail(), "Reset your Annadaan! password",
                    "We received a request to reset your password. Use this link within 30 minutes:\n\n"
                            + resetUrl + "\n\nIf you did not request this, you can ignore this email.");
        } catch (BrevoEmailDeliveryException e) {
            resetTokenRepository.delete(resetToken);
            logger.warn("Password reset email delivery failed; the reset token was revoked. Ensure BREVO_API_KEY is configured in backend/.env and sender [{}] is verified in Brevo.", senderAddress);
        }
    }

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";
    public static final String PASSWORD_REQUIREMENT_MESSAGE = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character";

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getPassword() == null || !request.getPassword().matches(PASSWORD_REGEX)) {
            throw new IllegalArgumentException(PASSWORD_REQUIREMENT_MESSAGE);
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashAndExpiresAtAfter(hashToken(request.getToken()), LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has expired. Request a new one."));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        resetTokenRepository.deleteByUserId(user.getId());
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}