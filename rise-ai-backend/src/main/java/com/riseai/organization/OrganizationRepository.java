package com.riseai.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsBySlug(String slug);

    Optional<Organization> findBySlug(String slug);

    Page<Organization> findByCreatedBy(UUID createdBy, Pageable pageable);

    @Query("select o from Organization o where (:status is null or o.verificationStatus = :status) and (:q is null or lower(o.name) like lower(concat('%', :q, '%')))")
    Page<Organization> search(String q, VerificationStatus status, Pageable pageable);

    @Query("select o.id as id, o.name as name, o.slug as slug, o.verificationStatus as verificationStatus from Organization o where o.id in :ids")
    List<SummaryView> findSummaries(Collection<UUID> ids);

    interface SummaryView {
        UUID getId();

        String getName();

        String getSlug();

        VerificationStatus getVerificationStatus();
    }
}
