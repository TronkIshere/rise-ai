package com.riseai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseai.entity.OutboxEvent;

public interface FindingIngestionService {
    String ENTITY = "MONITORING_FINDING";

    String CONTRACT_VERSION = "finding.v1";

    int ingest(OutboxEvent source, JsonNode findings);
}
