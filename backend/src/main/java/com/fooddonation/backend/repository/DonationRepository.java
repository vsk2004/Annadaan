package com.fooddonation.backend.repository;

import com.fooddonation.backend.entity.Donation;
import com.fooddonation.backend.entity.Donation.DonationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long> {

    // ── Donor queries ──────────────────────────────────────────
    Page<Donation> findByDonorIdOrderByCreatedAtDesc(Long donorId, Pageable pageable);

    List<Donation> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    long countByDonorId(Long donorId);
    long countByDonorIdAndStatus(Long donorId, DonationStatus status);

    // ── NGO queries ────────────────────────────────────────────
    List<Donation> findByAssignedNgoIdOrderByAcceptedAtDesc(Long ngoId);
    List<Donation> findByAssignedNgoIdAndStatusOrderByAcceptedAtDesc(Long ngoId, DonationStatus status);

    long countByAssignedNgoIdAndStatus(Long ngoId, DonationStatus status);

    // ── Expiry batch ───────────────────────────────────────────
    @Query("SELECT d FROM Donation d WHERE d.status = 'AVAILABLE' AND d.pickupDeadline < :now")
    List<Donation> findExpiredAvailable(@Param("now") LocalDateTime now);

    // ── Optimistic-locked single fetch for claim operation ─────
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT d FROM Donation d WHERE d.id = :id")
    Optional<Donation> findByIdWithLock(@Param("id") Long id);

    // ── Haversine nearby query (AVAILABLE, not expired) ────────
    @Query(value = """
            SELECT d.*, (
                6371 * ACOS(
                    COS(RADIANS(:ngoLat)) * COS(RADIANS(d.pickup_latitude))
                    * COS(RADIANS(d.pickup_longitude) - RADIANS(:ngoLng))
                    + SIN(RADIANS(:ngoLat)) * SIN(RADIANS(d.pickup_latitude))
                )
            ) AS distance_km
            FROM donations d
            WHERE d.status = 'AVAILABLE'
              AND d.pickup_deadline > NOW()
              AND d.assigned_ngo_id IS NULL
              AND (
                6371 * ACOS(
                    COS(RADIANS(:ngoLat)) * COS(RADIANS(d.pickup_latitude))
                    * COS(RADIANS(d.pickup_longitude) - RADIANS(:ngoLng))
                    + SIN(RADIANS(:ngoLat)) * SIN(RADIANS(d.pickup_latitude))
                )
              ) <= :radiusKm
            ORDER BY distance_km ASC
            """, nativeQuery = true)
    List<Object[]> findNearbyAvailableDonations(
            @Param("ngoLat") Double ngoLat,
            @Param("ngoLng") Double ngoLng,
            @Param("radiusKm") Double radiusKm
    );
}
