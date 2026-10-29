package com.fooddonation.backend.controller;

import com.fooddonation.backend.dto.*;
import com.fooddonation.backend.service.AuthService;
import com.fooddonation.backend.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired private AuthService authService;
    @Autowired private PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.sendResetLink(req.getEmail());
        return ResponseEntity.accepted().body(Map.of("message", "If an account matches this email, a reset link may be sent. If you do not receive one, try again later."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Password updated. You can now sign in."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            return ResponseEntity.ok(authService.login(req));
        } catch (DisabledException e) {
            return ResponseEntity.status(403).body(Map.of("error", "This account is suspended. Please contact support."));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Email or password is incorrect."));
        } catch (Exception e) {
            logger.error("Unexpected error during login", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Sign-in is temporarily unavailable. Please try again."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(authService.getCurrentUser(user.getUsername()));
    }
}
