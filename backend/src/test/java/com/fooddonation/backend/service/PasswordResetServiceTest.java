package com.fooddonation.backend.service;

import com.fooddonation.backend.dto.ResetPasswordRequest;
import com.fooddonation.backend.entity.PasswordResetToken;
import com.fooddonation.backend.entity.User;
import com.fooddonation.backend.exception.BrevoEmailDeliveryException;
import com.fooddonation.backend.repository.PasswordResetTokenRepository;
import com.fooddonation.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private BrevoTransactionalEmailSender emailSender;
    @InjectMocks private PasswordResetService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "https://annadaan.example");
        user = User.builder().id(7L).email("user@example.com").passwordHash("old-hash").build();
    }

    @Test
    void createsHashedExpiringTokenAndRequestsEmail() {
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
        when(resetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);

        service.sendResetLink(" user@example.com ");

        verify(resetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        assertEquals(44, savedToken.getTokenHash().length());
        LocalDateTime latestExpectedExpiry = LocalDateTime.now().plusMinutes(29);
        assertTrue(savedToken.getExpiresAt().isAfter(latestExpectedExpiry));
        verify(emailSender).sendResetEmail(eq(user.getEmail()), eq("Reset your Annadaan! password"),
            messageCaptor.capture());
        String rawToken = messageCaptor.getValue().split("\\?token=")[1].split("\\s")[0];
        assertFalse(rawToken.equals(savedToken.getTokenHash()));
        assertTrue(rawToken.length() >= 40);
    }

    @Test
    void doesNotRevealWhetherEmailExists() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        service.sendResetLink("unknown@example.com");

        verify(emailSender, never()).sendResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void revokesResetTokenWhenProviderRejectsEmail() {
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
        PasswordResetToken savedToken = PasswordResetToken.builder().id(3L).user(user).build();
        when(resetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(savedToken);
        doThrow(new BrevoEmailDeliveryException()).when(emailSender)
                .sendResetEmail(anyString(), anyString(), anyString());

        service.sendResetLink("user@example.com");

        verify(resetTokenRepository).delete(savedToken);
    }

    @Test
    void rejectsInvalidToken() {
        when(resetTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(request("invalid-token")));
    }

    @Test
    void rejectsExpiredToken() {
        when(resetTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(request("expired-token")));
    }

    @Test
    void changesPasswordAndInvalidatesTokens() {
        PasswordResetToken validToken = PasswordResetToken.builder()
                .id(3L).user(user).tokenHash("stored-hash").expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(resetTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(validToken));
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-password-hash");
        when(userRepository.save(user)).thenReturn(user);

        service.resetPassword(request("valid-token"));

        assertEquals("new-password-hash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(resetTokenRepository).deleteByUserId(user.getId());
    }

    private ResetPasswordRequest request(String token) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        request.setPassword("new-password-123");
        request.setConfirmPassword("new-password-123");
        return request;
    }
}