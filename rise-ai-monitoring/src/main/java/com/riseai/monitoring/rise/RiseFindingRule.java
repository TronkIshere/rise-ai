package com.riseai.monitoring.rise;

import com.riseai.monitoring.core.finding.Finding;

import java.util.List;

public interface RiseFindingRule {

    String getId();

    String getVersion();

    String getDescription();

    List<Finding> evaluate(RiseEnvelope envelope);
}
