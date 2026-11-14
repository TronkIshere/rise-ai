package com.riseai.repository;

import com.riseai.entity.ChainCommand;
import com.riseai.enums.ChainCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChainCommandRepository extends JpaRepository<ChainCommand, UUID> {
    @Query(value = "select id from chain_commands where status in ('PENDING', 'SUBMITTED') order by seq limit :limit", nativeQuery = true)
    List<String> findOpenIdsInOrder(int limit);

    @Query(value = "select * from chain_commands where campaign_id = :campaignId order by seq", nativeQuery = true)
    List<ChainCommand> findByCampaignInOrder(String campaignId);

    @Query(value = "select lower(tx_hash) from chain_commands where lower(tx_hash) in (:hashes)", nativeQuery = true)
    List<String> findRecordedTxHashes(Collection<String> hashes);

    boolean existsByReferenceIdAndActionInAndStatusIn(UUID referenceId, Collection<String> actions, Collection<ChainCommandStatus> statuses);

    long deleteByReferenceIdAndActionAndStatus(UUID referenceId, String action, ChainCommandStatus status);
}
