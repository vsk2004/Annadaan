package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.NgoProfile;
import com.fooddonation.backend.entity.NgoProfile.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NgoProfileRepository extends JpaRepository<NgoProfile, Long> {
    Optional<NgoProfile> findByUserId(Long userId);
    Optional<NgoProfile> findByUserEmail(String email);
    Optional<NgoProfile> findByUserEmailIgnoreCase(String email);
    boolean existsByUserId(Long userId);
}
