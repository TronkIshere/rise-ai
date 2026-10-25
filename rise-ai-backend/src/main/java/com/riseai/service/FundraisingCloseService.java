package com.riseai.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FundraisingCloseService {
    List<UUID> findDue(Instant now);

    boolean close(UUID campaignId, Instant now);
}
