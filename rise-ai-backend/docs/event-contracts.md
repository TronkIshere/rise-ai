# Event Contracts — IMPLEMENTED (schema version 1, 2026-10-25)

Canonical contract between `rise-ai-backend` (producer) and `rise-ai-monitoring` (consumer). Verified against `service/impl/DomainEventPublisherImpl.java`, `mapper/RiseEvents.java`, `client/MonitoringClient.java`, `service/impl/FindingIngestionServiceImpl.java` and the monitor's `rise` package. Design rationale: ADR-004, ADR-007.

## Production and delivery
1. A business service calls `DomainEventPublisher.publish(RiseEvents.<factory>(...))` **inside its own transaction** (`Propagation.MANDATORY`). The envelope is serialized once and stored in `outbox_events.envelope`. Rollback ⇒ no event; commit ⇒ event guaranteed. `ReconciliationService` publishes inside its `TransactionTemplate` callback because `run` itself is not transactional.
2. `OutboxRelay` (enabled by `rise.monitoring.enabled`) claims due rows with `FOR UPDATE SKIP LOCKED` and a lease in a short transaction, POSTs them to the monitor **outside any transaction**, then records the outcome and stores returned findings in one transaction.
3. **At-least-once.** A lost acknowledgement causes a resend; the monitor replays its stored result (`duplicate: true`), and the backend upserts findings by `findingId`, so duplicates are harmless.
4. Ordering is guaranteed only per aggregate (`occurred_at` order within a batch). Consumers must tolerate gaps, duplicates and late arrivals.

## Envelope
```json
{
  "eventId": "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d10",
  "eventType": "MILESTONE_EVIDENCE_SUBMITTED",
  "schemaVersion": 1,
  "occurredAt": "2026-10-25T12:14:01.123456Z",
  "producer": "rise-ai-backend",
  "aggregateType": "EVIDENCE",
  "aggregateId": "e924f86d-2743-46c3-8ac5-d38ef19c7e2c",
  "actorId": "c1d2…",
  "actorRole": "CAMPAIGN_CREATOR",
  "correlationId": "e2e-ev-2",
  "references": { "campaignId": "5212…", "milestoneId": "aee7…", "evidenceId": "e924…" },
  "payload": { "evidenceId": "e924…", "milestoneId": "aee7…", "campaignId": "5212…", "evidenceType": "RECEIPT", "fileHash": "637f…" }
}
```
| Field | Rule |
|---|---|
| `eventId` | Random UUID per outbox row; the consumer deduplication key |
| `occurredAt` | Commit-time `Instant`, truncated to microseconds; frozen in the row |
| `actorId` | Internal user UUID; omitted for system actions |
| `actorRole` | Role used for the action (`ADMIN`, `CAMPAIGN_CREATOR`, `DONOR`) or `SYSTEM` |
| `correlationId` | Request `X-Correlation-Id` captured at write time; omitted for scheduler-originated events |
| `references` | Whichever of `campaignId`, `milestoneId`, `disbursementRequestId`, `donationId`, `evidenceId` apply |
| Money | Decimal string with two places, plus `currency` (`"amount": "4000000.00", "currency": "VND"`) |
| Nulls | Omitted, never sent as `null` |

## Catalogue (20 event types)
| eventType | Aggregate | Payload fields | Emitted by |
|---|---|---|---|
| CAMPAIGN_CREATED | CAMPAIGN | campaignId, organizationId, creatorId, targetAmount, currency, category | `CampaignService.create` |
| CAMPAIGN_SUBMITTED_FOR_REVIEW | CAMPAIGN | campaignId, milestoneCount, targetAmount, currency | `CampaignService.submit` |
| CAMPAIGN_APPROVED | CAMPAIGN | campaignId, reviewerId | `CampaignService.approve` |
| CAMPAIGN_PAUSED | CAMPAIGN | campaignId | `CampaignService.pause` |
| DONATION_CONFIRMED | DONATION | donationId, campaignId, amount, currency, anonymous, ledgerTransactionCode | `DonationService.confirm` |
| MILESTONE_CREATED | MILESTONE | milestoneId, campaignId, sequenceNumber, plannedAmount, currency, dueAt | `MilestoneService.create` |
| MILESTONE_EVIDENCE_SUBMITTED | EVIDENCE | evidenceId, milestoneId, campaignId, disbursementRequestId?, evidenceType, fileHash? | `EvidenceService.submit` |
| EVIDENCE_ACCEPTED / EVIDENCE_REJECTED | EVIDENCE | evidenceId, milestoneId, campaignId, evidenceType | `EvidenceService.review` |
| DISBURSEMENT_REQUESTED | DISBURSEMENT_REQUEST | requestId, milestoneId, campaignId, amount, currency, milestonePlannedAmount, milestoneRemaining | `DisbursementService.create` |
| DISBURSEMENT_APPROVED | DISBURSEMENT_REQUEST | requestId, milestoneId, campaignId, amount, currency, reviewerId, acceptedEvidenceCount | `DisbursementService.approve` |
| DISBURSEMENT_REJECTED | DISBURSEMENT_REQUEST | requestId, milestoneId, campaignId, amount, currency | `DisbursementService.reject` |
| DISBURSEMENT_RELEASED | DISBURSEMENT_REQUEST | requestId, milestoneId, campaignId, amount, currency, ledgerTransactionCode, simulated: true | `DisbursementService.release` |
| DISPUTE_OPENED | DISPUTE | disputeId, campaignId, milestoneId?, disbursementRequestId?, disputeType | `DisputeService.open` |
| DISPUTE_RESOLVED | DISPUTE | disputeId, campaignId, milestoneId?, disputeType, outcome (`RESOLVED_UPHELD`/`RESOLVED_DISMISSED`) | `DisputeService.resolve` |
| REFUND_REQUESTED | REFUND_REQUEST | refundId, donationId, campaignId, amount, currency | `RefundService.request` |
| REFUND_RECORDED | REFUND_REQUEST | refundId, donationId, campaignId, amount, currency, ledgerTransactionCode, externalPaymentStatus | `RefundService.record` |
| ADJUSTMENT_APPLIED | ESCROW_ADJUSTMENT | adjustmentId, campaignId, direction, amount, currency, requestedBy, decidedBy | `AdjustmentService.approve` |
| SETTLEMENT_EXECUTED | SETTLEMENT_REQUEST | settlementId, campaignId, settlementType, amount, currency, donationCount | `SettlementService.approve` |
| RECONCILIATION_RUN_COMPLETED | RECONCILIATION_RUN | runId, runCode, runStatus, trigger, accountsChecked, inconsistentAccounts | `ReconciliationServiceImpl.save` |

Changes from the earlier design (no consumer existed, so these are inside v1): `CAMPAIGN_PAUSED` no longer carries the free-text `reason`; `actorRole` and `references` were added; seven event types were added (EVIDENCE_*, DISBURSEMENT_REJECTED, DISPUTE_RESOLVED, ADJUSTMENT_APPLIED, SETTLEMENT_EXECUTED, RECONCILIATION_RUN_COMPLETED); `MILESTONE_CREATED` gained `currency` and `dueAt`, `DISBURSEMENT_REQUESTED` gained `milestonePlannedAmount`.

## Data minimization
**Never in an envelope** (enforced by `DomainEventOutboxIntegrationTest`): titles, descriptions, reasons, notes, resolutions, `fileUrl`, `externalReference`, donor messages, names, emails, phone numbers, addresses, `beneficiaryDescription`, IP addresses, idempotency keys, simulated payment references. Login, registration and token events are not published.

## Mapping to the monitor request (`MonitoringClient`)
`eventId`, `eventType`, `occurredAt`, `correlationId` from the envelope; `source` = `producer`; `attributes` = `schemaVersion`, `aggregateType`, `actorRole` and scalar payload fields whose names do not end in `Id`, `Ids`, `Code`, `Hash` or `By`; `payload` = the stored envelope (byte-stable, part of the monitor's duplicate fingerprint). Header `Authorization: Bearer <rise.monitoring.service-key>` and `X-Correlation-Id`.

## Response classification (`MonitoringClient.classify`)
| Monitor response | Outbox outcome |
|---|---|
| 200 (any `monitoringStatus`) | `PUBLISHED`; findings ingested; `REJECTED` additionally logged and kept in `last_error` |
| 409/2002, 503/2003, 500/9000, 500/9999, other 5xx, 408, 429, timeout, connection error | `PENDING` with exponential backoff (5 s doubling, cap 15 min, ±20 % jitter); `FAILED` after `max-attempts` (12) |
| 400, 413, 409/2001, 401, 403, other 4xx | `FAILED` immediately |
| 200 with findings that violate `finding.v1` | `FAILED` ("Invalid finding contract"), nothing stored |

`FAILED` rows can be requeued by an admin (`POST /api/v1/admin/monitoring/outbox/{id}/requeue`, audited). Published rows are purged after `published-retention` (30 days).

## Finding contract `finding.v1` (monitor → backend)
`findingId` (64 lower-case hex, deterministic), `contractVersion`, `detector {type: RULE, id, version}`, `category`, `severity` (`INFO`…`CRITICAL`, fixed per rule, no numeric score), `subject {type, id}`, `references`, `sourceEventIds`, `facts [{name, value, sourceEventId}]` (≤ 20), `explanation` (template text, ≤ 2000 stored), `recommendedAction`, `observedAt`. The backend rejects the whole batch if any finding is malformed.

Backend semantics (`FindingIngestionService`): upsert by `findingId`; one occurrence row per (finding, source event), so redelivery adds nothing; severity only escalates; occurrences after a review increment `newOccurrencesSinceReview`; a `DISMISSED` finding whose severity escalates is reopened (`MONITORING_FINDING_REOPENED`, system actor).

## Compatibility rules
1. Within a `schemaVersion`, only add optional fields; never remove, rename or change types or meaning.
2. Consumers ignore unknown fields.
3. A breaking change creates a new `schemaVersion`; the monitor rejects versions not in `monitoring.rise.supported-schema-versions` with 400/2000, which dead-letters the row, so deploy the consumer first.
4. `eventType` names are permanent.
5. Ordering only per aggregate; tolerate gaps and duplicates.

## AI note persistence (2026-10-30)
The optional `aiNote` (finding.v1, at most 400 characters) is stored on the `monitoring_finding_occurrences` row of the event that carried it (`ai_note`, migration V11), after control characters and whitespace runs are collapsed. It is advisory: it never alters severity, explanation, facts, recommended action or the finding key, and it is returned only through the ADMIN finding detail endpoint as `occurrences[].aiNote`. The contract has no model identifier, so none is stored.
