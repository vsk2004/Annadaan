package com.fooddonation.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ngo_profiles", indexes = {
    @Index(name = "idx_ngo_user",   columnList = "user_id"),
    @Index(name = "idx_ngo_status", columnList = "verification_status"),
    @Index(name = "idx_ngo_loc",    columnList = "latitude,longitude")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NgoProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    private String organizationName;

    private String registrationNumber;

    private String organizationType;

    private String contactPerson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String ngoRegistrationInfo;

    private String address;
    private String city;
    private String state;
    private String postalCode;

    private Double latitude;
    private Double longitude;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }
}
