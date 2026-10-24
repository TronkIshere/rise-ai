package com.riseai.donation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonationRepository extends JpaRepository<Donation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.id = :id")
    Optional<Donation> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.campaignId = :campaignId and d.donationStatus in :statuses order by d.id")
    List<Donation> lockByCampaignIdAndDonationStatusIn(UUID campaignId, Collection<DonationStatus> statuses);

    Optional<Donation> findByDonorIdAndIdempotencyKey(UUID donorId, String idempotencyKey);

    Page<Donation> findByDonorId(UUID donorId, Pageable pageable);

    Page<Donation> findByCampaignIdAndDonationStatusIn(UUID campaignId, Collection<DonationStatus> statuses, Pageable pageable);

    Page<Donation> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<Donation> findByDonationStatus(DonationStatus status, Pageable pageable);

    boolean existsByCampaignIdAndDonorIdAndDonationStatusIn(UUID campaignId, UUID donorId, Collection<DonationStatus> statuses);

    long countByCampaignIdAndDonationStatusIn(UUID campaignId, Collection<DonationStatus> statuses);

    long countByDonationStatus(DonationStatus status);

    @Query("select count(distinct d.donorId) from Donation d where d.campaignId = :campaignId and d.donationStatus in :statuses")
    long countDistinctDonors(UUID campaignId, Collection<DonationStatus> statuses);
}
