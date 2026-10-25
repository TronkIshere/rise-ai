package com.riseai.finding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FindingOccurrenceRepository extends JpaRepository<FindingOccurrence, UUID> {
    boolean existsByFindingIdAndSourceEventId(UUID findingId, UUID sourceEventId);

    List<FindingOccurrence> findByFindingIdOrderByObservedAtAsc(UUID findingId);

    @Query("select distinct o.actorId from FindingOccurrence o where o.findingId = :findingId and o.actorId is not null")
    List<UUID> actorIds(UUID findingId);
}
