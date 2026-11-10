package com.riseai.repository;

import com.riseai.entity.ChainAnchor;
import com.riseai.enums.ChainCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ChainAnchorRepository extends JpaRepository<ChainAnchor, UUID> {
    Optional<ChainAnchor> findFirstByKindAndStatusInOrderByCreatedAtDesc(String kind, Collection<ChainCommandStatus> statuses);

    Optional<ChainAnchor> findFirstByKindAndRootAndStatus(String kind, String root, ChainCommandStatus status);
}
