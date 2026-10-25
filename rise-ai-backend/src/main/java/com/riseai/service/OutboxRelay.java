package com.riseai.service;

public interface OutboxRelay {
    RelayRun relayOnce();

    int purgePublished();

    record RelayRun(int claimed, int published, int retried, int failed) {
    }
}
