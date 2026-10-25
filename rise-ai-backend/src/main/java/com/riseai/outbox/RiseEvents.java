package com.riseai.outbox;

import com.riseai.adjustment.EscrowAdjustment;
import com.riseai.campaign.Campaign;
import com.riseai.disbursement.DisbursementRequest;
import com.riseai.dispute.Dispute;
import com.riseai.donation.Donation;
import com.riseai.escrow.EscrowLedgerEntry;
import com.riseai.escrow.ReconciliationRun;
import com.riseai.escrow.ReconciliationTrigger;
import com.riseai.evidence.EvidenceRecord;
import com.riseai.milestone.Milestone;
import com.riseai.refund.RefundRequest;
import com.riseai.security.AuthUser;
import com.riseai.settlement.SettlementRequest;
import com.riseai.user.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RiseEvents {
    public static final String CAMPAIGN_CREATED = "CAMPAIGN_CREATED";
    public static final String CAMPAIGN_SUBMITTED_FOR_REVIEW = "CAMPAIGN_SUBMITTED_FOR_REVIEW";
    public static final String CAMPAIGN_APPROVED = "CAMPAIGN_APPROVED";
    public static final String CAMPAIGN_PAUSED = "CAMPAIGN_PAUSED";
    public static final String DONATION_CONFIRMED = "DONATION_CONFIRMED";
    public static final String MILESTONE_CREATED = "MILESTONE_CREATED";
    public static final String MILESTONE_EVIDENCE_SUBMITTED = "MILESTONE_EVIDENCE_SUBMITTED";
    public static final String EVIDENCE_ACCEPTED = "EVIDENCE_ACCEPTED";
    public static final String EVIDENCE_REJECTED = "EVIDENCE_REJECTED";
    public static final String DISBURSEMENT_REQUESTED = "DISBURSEMENT_REQUESTED";
    public static final String DISBURSEMENT_APPROVED = "DISBURSEMENT_APPROVED";
    public static final String DISBURSEMENT_REJECTED = "DISBURSEMENT_REJECTED";
    public static final String DISBURSEMENT_RELEASED = "DISBURSEMENT_RELEASED";
    public static final String DISPUTE_OPENED = "DISPUTE_OPENED";
    public static final String DISPUTE_RESOLVED = "DISPUTE_RESOLVED";
    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";
    public static final String REFUND_RECORDED = "REFUND_RECORDED";
    public static final String ADJUSTMENT_APPLIED = "ADJUSTMENT_APPLIED";
    public static final String SETTLEMENT_EXECUTED = "SETTLEMENT_EXECUTED";
    public static final String RECONCILIATION_RUN_COMPLETED = "RECONCILIATION_RUN_COMPLETED";

    private RiseEvents() {
    }

    public static DomainEvent campaignCreated(Campaign campaign, AuthUser actor) {
        return event(CAMPAIGN_CREATED, "CAMPAIGN", campaign.getId(), actor, Role.CAMPAIGN_CREATOR,
                refs("campaignId", campaign.getId()),
                fields("campaignId", campaign.getId(), "organizationId", campaign.getOrganizationId(), "creatorId", campaign.getCreatorId(),
                        "targetAmount", campaign.getTargetAmount(), "currency", campaign.getCurrency(), "category", campaign.getCategory()));
    }

    public static DomainEvent campaignSubmitted(Campaign campaign, int milestoneCount, AuthUser actor) {
        return event(CAMPAIGN_SUBMITTED_FOR_REVIEW, "CAMPAIGN", campaign.getId(), actor, Role.CAMPAIGN_CREATOR,
                refs("campaignId", campaign.getId()),
                fields("campaignId", campaign.getId(), "milestoneCount", milestoneCount, "targetAmount", campaign.getTargetAmount(),
                        "currency", campaign.getCurrency()));
    }

    public static DomainEvent campaignApproved(Campaign campaign, AuthUser actor) {
        return event(CAMPAIGN_APPROVED, "CAMPAIGN", campaign.getId(), actor, Role.ADMIN,
                refs("campaignId", campaign.getId()),
                fields("campaignId", campaign.getId(), "reviewerId", actorId(actor)));
    }

    public static DomainEvent campaignPaused(Campaign campaign, AuthUser actor) {
        return event(CAMPAIGN_PAUSED, "CAMPAIGN", campaign.getId(), actor, Role.ADMIN,
                refs("campaignId", campaign.getId()),
                fields("campaignId", campaign.getId()));
    }

    public static DomainEvent donationConfirmed(Donation donation, EscrowLedgerEntry entry, AuthUser actor) {
        return event(DONATION_CONFIRMED, "DONATION", donation.getId(), actor, Role.ADMIN,
                refs("campaignId", donation.getCampaignId(), "donationId", donation.getId()),
                fields("donationId", donation.getId(), "campaignId", donation.getCampaignId(), "amount", donation.getAmount(),
                        "currency", donation.getCurrency(), "anonymous", donation.isAnonymous(),
                        "ledgerTransactionCode", entry == null ? null : entry.getTransactionCode()));
    }

    public static DomainEvent milestoneCreated(Milestone milestone, String currency, AuthUser actor) {
        return event(MILESTONE_CREATED, "MILESTONE", milestone.getId(), actor, Role.CAMPAIGN_CREATOR,
                refs("campaignId", milestone.getCampaignId(), "milestoneId", milestone.getId()),
                fields("milestoneId", milestone.getId(), "campaignId", milestone.getCampaignId(), "sequenceNumber", milestone.getSequenceNumber(),
                        "plannedAmount", milestone.getPlannedAmount(), "currency", currency, "dueAt", milestone.getDueAt()));
    }

    public static DomainEvent evidenceSubmitted(EvidenceRecord evidence, AuthUser actor) {
        return event(MILESTONE_EVIDENCE_SUBMITTED, "EVIDENCE", evidence.getId(), actor, Role.CAMPAIGN_CREATOR,
                refs("campaignId", evidence.getCampaignId(), "milestoneId", evidence.getMilestoneId(),
                        "disbursementRequestId", evidence.getDisbursementRequestId(), "evidenceId", evidence.getId()),
                fields("evidenceId", evidence.getId(), "milestoneId", evidence.getMilestoneId(), "campaignId", evidence.getCampaignId(),
                        "disbursementRequestId", evidence.getDisbursementRequestId(), "evidenceType", evidence.getEvidenceType(),
                        "fileHash", evidence.getFileHash()));
    }

    public static DomainEvent evidenceReviewed(EvidenceRecord evidence, boolean accepted, AuthUser actor) {
        return event(accepted ? EVIDENCE_ACCEPTED : EVIDENCE_REJECTED, "EVIDENCE", evidence.getId(), actor, Role.ADMIN,
                refs("campaignId", evidence.getCampaignId(), "milestoneId", evidence.getMilestoneId(), "evidenceId", evidence.getId()),
                fields("evidenceId", evidence.getId(), "milestoneId", evidence.getMilestoneId(), "campaignId", evidence.getCampaignId(),
                        "evidenceType", evidence.getEvidenceType()));
    }

    public static DomainEvent disbursementRequested(DisbursementRequest request, Milestone milestone, AuthUser actor) {
        return event(DISBURSEMENT_REQUESTED, "DISBURSEMENT_REQUEST", request.getId(), actor, Role.CAMPAIGN_CREATOR,
                disbursementRefs(request),
                fields("requestId", request.getId(), "milestoneId", request.getMilestoneId(), "campaignId", request.getCampaignId(),
                        "amount", request.getRequestedAmount(), "currency", request.getCurrency(),
                        "milestonePlannedAmount", milestone.getPlannedAmount(), "milestoneRemaining", milestone.remainingAmount()));
    }

    public static DomainEvent disbursementApproved(DisbursementRequest request, long acceptedEvidenceCount, AuthUser actor) {
        return event(DISBURSEMENT_APPROVED, "DISBURSEMENT_REQUEST", request.getId(), actor, Role.ADMIN,
                disbursementRefs(request),
                fields("requestId", request.getId(), "milestoneId", request.getMilestoneId(), "campaignId", request.getCampaignId(),
                        "amount", request.getRequestedAmount(), "currency", request.getCurrency(), "reviewerId", actorId(actor),
                        "acceptedEvidenceCount", acceptedEvidenceCount));
    }

    public static DomainEvent disbursementRejected(DisbursementRequest request, AuthUser actor) {
        return event(DISBURSEMENT_REJECTED, "DISBURSEMENT_REQUEST", request.getId(), actor, Role.ADMIN,
                disbursementRefs(request),
                fields("requestId", request.getId(), "milestoneId", request.getMilestoneId(), "campaignId", request.getCampaignId(),
                        "amount", request.getRequestedAmount(), "currency", request.getCurrency()));
    }

    public static DomainEvent disbursementReleased(DisbursementRequest request, EscrowLedgerEntry entry, AuthUser actor) {
        return event(DISBURSEMENT_RELEASED, "DISBURSEMENT_REQUEST", request.getId(), actor, Role.ADMIN,
                disbursementRefs(request),
                fields("requestId", request.getId(), "milestoneId", request.getMilestoneId(), "campaignId", request.getCampaignId(),
                        "amount", request.getRequestedAmount(), "currency", request.getCurrency(),
                        "ledgerTransactionCode", entry == null ? null : entry.getTransactionCode(), "simulated", true));
    }

    public static DomainEvent disputeOpened(Dispute dispute, AuthUser actor) {
        return event(DISPUTE_OPENED, "DISPUTE", dispute.getId(), actor, Role.DONOR,
                refs("campaignId", dispute.getCampaignId(), "milestoneId", dispute.getMilestoneId(),
                        "disbursementRequestId", dispute.getDisbursementRequestId()),
                fields("disputeId", dispute.getId(), "campaignId", dispute.getCampaignId(), "milestoneId", dispute.getMilestoneId(),
                        "disbursementRequestId", dispute.getDisbursementRequestId(), "disputeType", dispute.getDisputeType()));
    }

    public static DomainEvent disputeResolved(Dispute dispute, AuthUser actor) {
        return event(DISPUTE_RESOLVED, "DISPUTE", dispute.getId(), actor, Role.ADMIN,
                refs("campaignId", dispute.getCampaignId(), "milestoneId", dispute.getMilestoneId()),
                fields("disputeId", dispute.getId(), "campaignId", dispute.getCampaignId(), "milestoneId", dispute.getMilestoneId(),
                        "disputeType", dispute.getDisputeType(), "outcome", dispute.getDisputeStatus()));
    }

    public static DomainEvent refundRequested(RefundRequest refund, AuthUser actor) {
        return event(REFUND_REQUESTED, "REFUND_REQUEST", refund.getId(), actor, Role.DONOR,
                refs("campaignId", refund.getCampaignId(), "donationId", refund.getDonationId()),
                fields("refundId", refund.getId(), "donationId", refund.getDonationId(), "campaignId", refund.getCampaignId(),
                        "amount", refund.getAmount(), "currency", refund.getCurrency()));
    }

    public static DomainEvent refundRecorded(RefundRequest refund, EscrowLedgerEntry entry, AuthUser actor) {
        return event(REFUND_RECORDED, "REFUND_REQUEST", refund.getId(), actor, Role.ADMIN,
                refs("campaignId", refund.getCampaignId(), "donationId", refund.getDonationId()),
                fields("refundId", refund.getId(), "donationId", refund.getDonationId(), "campaignId", refund.getCampaignId(),
                        "amount", refund.getAmount(), "currency", refund.getCurrency(),
                        "ledgerTransactionCode", entry == null ? null : entry.getTransactionCode(),
                        "externalPaymentStatus", refund.getExternalPaymentStatus()));
    }

    public static DomainEvent adjustmentApplied(EscrowAdjustment adjustment, AuthUser actor) {
        return event(ADJUSTMENT_APPLIED, "ESCROW_ADJUSTMENT", adjustment.getId(), actor, Role.ADMIN,
                refs("campaignId", adjustment.getCampaignId()),
                fields("adjustmentId", adjustment.getId(), "campaignId", adjustment.getCampaignId(), "direction", adjustment.getDirection(),
                        "amount", adjustment.getAmount(), "currency", adjustment.getCurrency(),
                        "requestedBy", adjustment.getRequestedBy(), "decidedBy", adjustment.getDecidedBy()));
    }

    public static DomainEvent settlementExecuted(SettlementRequest settlement, AuthUser actor) {
        return event(SETTLEMENT_EXECUTED, "SETTLEMENT_REQUEST", settlement.getId(), actor, Role.ADMIN,
                refs("campaignId", settlement.getCampaignId()),
                fields("settlementId", settlement.getId(), "campaignId", settlement.getCampaignId(),
                        "settlementType", settlement.getSettlementType(), "amount", settlement.getAmount(),
                        "currency", settlement.getCurrency(), "donationCount", settlement.getDonationCount()));
    }

    public static DomainEvent reconciliationCompleted(ReconciliationRun run) {
        boolean manual = run.getTriggerType() == ReconciliationTrigger.MANUAL && run.getTriggeredBy() != null;
        return new DomainEvent(RECONCILIATION_RUN_COMPLETED, "RECONCILIATION_RUN", run.getId(), run.getTriggeredBy(),
                manual ? Role.ADMIN.name() : DomainEvent.SYSTEM_ROLE, Map.of(),
                fields("runId", run.getId(), "runCode", run.getRunCode(), "runStatus", run.getRunStatus(), "trigger", run.getTriggerType(),
                        "accountsChecked", run.getAccountsChecked(), "inconsistentAccounts", run.getInconsistentAccounts()));
    }

    private static DomainEvent event(String type, String aggregateType, UUID aggregateId, AuthUser actor, Role expectedRole,
                                     Map<String, Object> references, Map<String, Object> payload) {
        return new DomainEvent(type, aggregateType, aggregateId, actorId(actor), role(actor, expectedRole), references, payload);
    }

    private static Map<String, Object> disbursementRefs(DisbursementRequest request) {
        return refs("campaignId", request.getCampaignId(), "milestoneId", request.getMilestoneId(), "disbursementRequestId", request.getId());
    }

    private static UUID actorId(AuthUser actor) {
        return actor == null ? null : actor.id();
    }

    private static String role(AuthUser actor, Role expected) {
        if (actor == null) {
            return DomainEvent.SYSTEM_ROLE;
        }
        if (actor.has(expected)) {
            return expected.name();
        }
        if (actor.isAdmin()) {
            return Role.ADMIN.name();
        }
        return actor.roles().stream().map(Role::name).sorted().findFirst().orElse(DomainEvent.SYSTEM_ROLE);
    }

    private static Map<String, Object> refs(Object... keyValues) {
        return fields(keyValues);
    }

    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put(String.valueOf(keyValues[i]), normalize(value));
            }
        }
        return map;
    }

    private static Object normalize(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof UUID || value instanceof Enum<?> || value instanceof Instant) {
            return value.toString();
        }
        return value;
    }
}
