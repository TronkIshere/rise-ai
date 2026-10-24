package com.riseai.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    boolean existsBySlug(String slug);

    Optional<Campaign> findBySlug(String slug);

    boolean existsByOrganizationId(UUID organizationId);

    Page<Campaign> findByCreatorId(UUID creatorId, Pageable pageable);

    @Query("select c from Campaign c where c.campaignStatus in :statuses and (:category is null or c.category = :category) and (:organizationId is null or c.organizationId = :organizationId) and (:q is null or lower(c.title) like lower(concat('%', :q, '%')) or lower(c.location) like lower(concat('%', :q, '%')) or lower(c.shortDescription) like lower(concat('%', :q, '%')))")
    Page<Campaign> search(Collection<CampaignStatus> statuses, CampaignCategory category, UUID organizationId, String q, Pageable pageable);

    @Query("select c.campaignStatus, count(c) from Campaign c group by c.campaignStatus")
    List<Object[]> countGroupedByStatus();
}
