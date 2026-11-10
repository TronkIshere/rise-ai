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

    boolean existsByReferenceIdAndActionInAndStatusIn(UUID referenceId, Collection<String> actions, Collection<ChainCommandStatus> statuses);

    long deleteByReferenceIdAndActionAndStatus(UUID referenceId, String action, ChainCommandStatus status);
}
