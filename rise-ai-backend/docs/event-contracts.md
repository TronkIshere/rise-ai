# Event Contracts — DESIGNED, NOT IMPLEMENTED

No events are published in Phase 0. These contracts define what Phase 2 must emit.

## Envelope (schemaVersion 1)
```json
{
  "eventId": "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d10",
  "eventType": "DISBURSEMENT_RELEASED",
  "schemaVersion": 1,
  "occurredAt": "2026-10-24T08:15:30.123Z",
  "producer": "rise-ai-backend",
  "aggregateType": "DISBURSEMENT_REQUEST",
  "aggregateId": "7e0b…",
  "actorId": "c1d2…",
  "correlationId": "b7a9…",
  "payload": { }
}
```
- `eventId`: UUID, unique per event, used for consumer deduplication.
- `actorId`: internal user id; omitted for system actions. No emails, names or tokens.
- Money: string decimal + currency (`"amount": "40000000.00", "currency": "VND"`).

## Events and payloads
| eventType | aggregateType | Payload fields | Source (Phase 0 code) |
|---|---|---|---|
| CAMPAIGN_CREATED | CAMPAIGN | campaignId, organizationId, creatorId, targetAmount, currency, category | CampaignService.create |
| CAMPAIGN_SUBMITTED_FOR_REVIEW | CAMPAIGN | campaignId, milestoneCount | submit |
| CAMPAIGN_APPROVED | CAMPAIGN | campaignId, reviewerId | approve |
| CAMPAIGN_PAUSED | CAMPAIGN | campaignId, reason | pause |
| DONATION_CONFIRMED | DONATION | donationId, campaignId, amount, currency, anonymous, ledgerTransactionCode | DonationService.confirm |
| MILESTONE_CREATED | MILESTONE | milestoneId, campaignId, sequenceNumber, plannedAmount | MilestoneService.create |
| MILESTONE_EVIDENCE_SUBMITTED | EVIDENCE | evidenceId, milestoneId, disbursementRequestId?, evidenceType, fileHash? | EvidenceService.submit |
| DISBURSEMENT_REQUESTED | DISBURSEMENT_REQUEST | requestId, milestoneId, campaignId, amount, currency, milestoneRemaining | DisbursementService.create |
| DISBURSEMENT_APPROVED | DISBURSEMENT_REQUEST | requestId, reviewerId, acceptedEvidenceCount | approve |
| DISBURSEMENT_RELEASED | DISBURSEMENT_REQUEST | requestId, milestoneId, amount, currency, ledgerTransactionCode, simulated: true | release |
| DISPUTE_OPENED | DISPUTE | disputeId, campaignId, milestoneId?, disbursementRequestId?, disputeType | DisputeService.open |
| REFUND_REQUESTED | REFUND_REQUEST | refundId, donationId, campaignId, amount, currency | RefundService.request |
| REFUND_RECORDED | REFUND_REQUEST | refundId, donationId, amount, currency, ledgerTransactionCode, externalPaymentStatus | record |

Example `DISBURSEMENT_REQUESTED` payload:
```json
{ "requestId": "7e0b…", "milestoneId": "41aa…", "campaignId": "9c3e…",
  "amount": "20000000.00", "currency": "VND", "milestoneRemaining": "30000000.00" }
```

## Compatibility rules
1. Within a `schemaVersion`, only add optional fields; never remove, rename or change types/meaning.
2. Consumers must ignore unknown fields.
3. Breaking change ⇒ new `schemaVersion`; producer may emit both versions during migration.
4. `eventType` names are permanent.
5. Ordering is guaranteed only per `aggregateId` (relay publishes in `occurred_at` order per aggregate); consumers must tolerate gaps and duplicates.
