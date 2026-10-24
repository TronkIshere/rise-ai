package com.riseai.refund;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<RefundRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(UUID id);

    boolean existsByDonationIdAndRefundStatusIn(UUID donationId, Collection<RefundStatus> statuses);

    Page<RefundRequest> findByRequestedBy(UUID requestedBy, Pageable pageable);

    Page<RefundRequest> findByRefundStatus(RefundStatus status, Pageable pageable);

    long countByRefundStatusIn(Collection<RefundStatus> statuses);
}
