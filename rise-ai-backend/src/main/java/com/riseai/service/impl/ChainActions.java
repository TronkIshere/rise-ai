package com.riseai.service.impl;

/** Gateway action names. Each one is exactly one CampaignEscrow transaction. */
final class ChainActions {
    static final String OPEN = "open";
    static final String STATUS = "status";
    static final String CONTRIBUTION = "contribution";
    static final String RESERVE = "reserve";
    static final String CANCEL_RESERVATION = "cancel-reservation";
    static final String QUEUE_RELEASE = "queue-release";
    static final String EXECUTE_RELEASE = "execute-release";
    static final String RESERVE_REFUND = "reserve-refund";
    static final String RELEASE_REFUND_RESERVATION = "release-refund-reservation";
    static final String RECORD_REFUND = "record-refund";
    static final String SETTLEMENT_REFUND = "settlement-refund";
    static final String ADJUSTMENT = "adjustment";
    static final String ANCHOR_EVIDENCE = "anchor-evidence";
    static final String DECIDE_EVIDENCE = "decide-evidence";
    static final String OPEN_DISPUTE = "open-dispute";
    static final String CLOSE_DISPUTE = "close-dispute";

    private ChainActions() {
    }
}
