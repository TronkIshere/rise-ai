package com.riseai.monitoring.testsupport;

import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.result.MonitoringResultPublisher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingResultPublisher implements MonitoringResultPublisher {

    private final List<MonitoringResult> results = new CopyOnWriteArrayList<>();

    @Override
    public void publish(MonitoringResult result) {
        results.add(result);
    }

    public List<MonitoringResult> getResults() {
        return List.copyOf(results);
    }
}
