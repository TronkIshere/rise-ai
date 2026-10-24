# API Specification — IMPLEMENTED

Base path `/api/v1`. Live OpenAPI: `/v3/api-docs`, Swagger UI: `/swagger-ui.html`. Authentication: `Authorization: Bearer <accessToken>`.

## Envelopes
Success:
```json
{ "code": 200, "message": "success", "data": { } }
```
Paged `data`: `{ "currentPage", "pageSize", "totalPages", "totalElements", "data": [] }`. Paging params: `page`, `size` (max 100), `sort=field,dir`.

Error (`ApiError`):
```json
{ "timestamp": "2026-10-24T10:00:00Z", "status": 422, "code": "MILESTONE_BUDGET_EXCEEDED",
  "message": "Requested 60000000 exceeds remaining milestone budget 40000000",
  "path": "/api/v1/milestones/…/disbursements", "correlationId": "…", "violations": [{"field":"amount","message":"…"}] }
```
Stack traces, SQL messages and exception class names are never returned.

## Error codes
| Code | HTTP | Meaning |
|---|---|---|
| UNAUTHENTICATED, INVALID_CREDENTIALS, REFRESH_TOKEN_INVALID | 401 | Missing/invalid auth |
| ACCESS_DENIED, ACCOUNT_SUSPENDED, SELF_APPROVAL_NOT_ALLOWED, CONFLICT_OF_INTEREST, SELF_ROLE_CHANGE_NOT_ALLOWED, DISPUTE_NOT_ELIGIBLE, SIMULATION_DISABLED | 403 | Not permitted |
| VALIDATION_FAILED, INVALID_REQUEST, UNSUPPORTED_CURRENCY | 400 | Bad input |
| RESOURCE_NOT_FOUND | 404 | Missing **or not visible to caller** |
| EMAIL_ALREADY_EXISTS, INVALID_STATE_TRANSITION, CAMPAIGN_NOT_ACCEPTING_DONATIONS, DUPLICATE_OPERATION, OPEN_REQUEST_EXISTS, RESOURCE_IN_USE, CONCURRENT_MODIFICATION, DATA_INTEGRITY_VIOLATION, ESCROW_NOT_ACTIVE | 409 | Conflict with current state |
| BUDGET_MISMATCH, MILESTONE_BUDGET_EXCEEDED, DONATION_BELOW_MINIMUM, INSUFFICIENT_AVAILABLE_BALANCE, EVIDENCE_REQUIRED, REFUND_EXCEEDS_REFUNDABLE, ORGANIZATION_NOT_VERIFIED | 422 | Business rule violated |
| INTERNAL_ERROR | 500 | Unexpected; details only in server log |

## Endpoints
Legend: P = public (no token), A = any authenticated, D = DONOR, C = CAMPAIGN_CREATOR, ADM = ADMIN, O = resource owner.

### Auth
| Method | Path | Access | Body / notes |
|---|---|---|---|
| POST | /auth/register | P | `{email,password,fullName,phone?,accountType: DONOR\|CAMPAIGN_CREATOR}`; any other role → 400 |
| POST | /auth/login | P | `{email,password}` → `{accessToken,tokenType,expiresIn,refreshToken,refreshTokenExpiresAt,user}` |
| POST | /auth/refresh | P | `{refreshToken}`; rotates; reuse of a rotated token revokes all user tokens |
| POST | /auth/logout | P | `{refreshToken}`; revokes it (idempotent) |
| GET | /auth/me | A | current profile |

### Users
| GET/PUT | /users/me | A | PUT `{fullName,phone?,avatarUrl?}` |
| GET | /admin/users?q&status | ADM | |
| GET | /admin/users/{id} | ADM | |
| POST | /admin/users/{id}/suspend \| /reactivate | ADM | `{reason}`; not on self; suspension revokes refresh tokens |
| PUT | /admin/users/{id}/roles | ADM | `{roles:[…]}`; not on self |

### Organizations
| POST | /organizations | C, ADM | `{name,description?,organizationType,contactEmail,contactPhone?,address?}` |
| GET | /organizations?q&status | P | |
| GET | /organizations/mine | A | |
| GET | /organizations/{id} | P | includes `verificationDisclaimer` |
| PUT/DELETE | /organizations/{id} | O, ADM | delete only when no campaigns reference it |
| POST | /organizations/{id}/submit-verification | O | UNVERIFIED/REJECTED → PENDING_REVIEW |
| POST | /admin/organizations/{id}/verify \| /reject-verification | ADM (not owner) | `{notes}` |

### Campaigns
| POST | /campaigns | C | CampaignRequest (organizationId, title, shortDescription, fullDescription, coverImageUrl?, galleryImageUrls?, category, beneficiaryDescription, targetAmount, currency?, minimumDonationAmount, startAt, endAt, location, beneficiaryCount, problemStatement, solutionDescription, impactGoals, budgetBreakdown?) |
| PUT | /campaigns/{id} | O (DRAFT/REJECTED) | same body |
| GET | /campaigns?status&category&organizationId&q | P | non-admins see ACTIVE/PAUSED/COMPLETED only |
| GET | /campaigns/mine | C | |
| GET | /campaigns/{id}, /campaigns/slug/{slug} | P* | drafts visible to owner/admin only (else 404) |
| GET | /campaigns/{id}/statistics, /history | P* | |
| POST | /campaigns/{id}/submit | O | |
| POST | /campaigns/{id}/approve | ADM (not creator) | `{reason?}` |
| POST | /campaigns/{id}/reject | ADM | `{reason}` |
| POST | /campaigns/{id}/publish | O, ADM | APPROVED → ACTIVE; opens escrow |
| POST | /campaigns/{id}/pause \| /resume \| /complete | ADM | pause requires reason |
| POST | /campaigns/{id}/cancel | O (pre-publication), ADM | `{reason}` |

### Milestones & evidence
| POST/GET | /campaigns/{campaignId}/milestones | C,O / P* | MilestoneRequest `{title,description?,plannedAmount,startAt,dueAt,releaseConditions,expectedDeliverables}` |
| GET/PUT/DELETE | /milestones/{id} | P* / O (campaign editable) | GET includes progress and evidenceCount |
| GET | /milestones/{id}/history | P* | |
| POST | /milestones/{id}/start | O | READY → IN_PROGRESS |
| POST | /milestones/{id}/complete \| /cancel | ADM | |
| POST/GET | /milestones/{milestoneId}/evidence | O / P* | `{evidenceType,title,description?,fileUrl?(https:// or sim://),fileHash?(sha256),externalReference?}` |
| GET | /evidence/{id} | P* | |
| POST | /evidence/{id}/accept \| /reject | ADM (not submitter/creator) | |

### Donations & refunds
| POST | /campaigns/{campaignId}/donations | D | `{amount,currency?,paymentMethod?,anonymous?,donorMessage?}`; optional `Idempotency-Key` header |
| GET | /campaigns/{campaignId}/donations | P* | confirmed donations, donor masked |
| GET | /campaigns/{campaignId}/donations/manage?status | O, ADM | |
| GET | /donations/me | A | |
| GET | /donations/{id} | own donor, ADM, campaign owner (masked) | |
| POST | /donations/{id}/cancel | own donor | PENDING only |
| GET | /admin/donations?status | ADM | |
| POST | /admin/donations/{id}/confirm | ADM (not the donor) | `{simulatedReference?}`; records ledger contribution |
| POST | /admin/donations/{id}/fail | ADM | `{reason}` |
| POST | /donations/{donationId}/refunds | own donor, ADM | `{amount,reason}` |
| GET | /refunds/me, /refunds/{id} | A (own) | |
| POST | /refunds/{id}/cancel | requester | |
| GET | /admin/refunds?status | ADM | |
| POST | /admin/refunds/{id}/approve \| /reject \| /record | ADM (not requester/donor) | record writes REFUND_RECORDED |

### Escrow
| GET | /campaigns/{campaignId}/escrow | P* | summary with `simulated:true` and disclaimer |
| GET | /campaigns/{campaignId}/escrow/ledger?type | P* | paged ledger |
| GET | /admin/escrow/campaigns/{campaignId}/reconciliation | ADM | stored vs ledger-computed balances |
| GET | /admin/escrow/reconciliation | ADM | all accounts |

### Disbursements
| POST | /milestones/{milestoneId}/disbursements | O | `{amount,reason}`; reserves funds |
| GET | /campaigns/{campaignId}/disbursements | P* | |
| GET | /disbursements/{id}, /history, /evidence | P* | |
| POST | /disbursements/{id}/evidence | O | additional evidence |
| POST | /disbursements/{id}/resubmit \| /cancel | O | |
| POST | /disbursements/{id}/start-review \| /request-evidence \| /approve \| /reject \| /release | ADM (not requester/creator) | release = simulated, no transfer |
| GET | /admin/disbursements?status | ADM | |

### Disputes
| POST | /campaigns/{campaignId}/disputes | eligible donor, ADM | `{milestoneId?,disbursementRequestId?,disputeType,title,description,supportingReferences?}` |
| GET | /campaigns/{campaignId}/disputes | O, ADM | |
| GET | /disputes/me | A | |
| GET | /disputes/{id}, /history, /messages | participant | opener, campaign creator, ADM |
| POST | /disputes/{id}/messages | participant | `{message}` |
| POST | /disputes/{id}/withdraw | opener | |
| GET | /admin/disputes?status | ADM | |
| POST | /admin/disputes/{id}/assign | ADM | `{reviewerId}` — active ADMIN, not a party |
| POST | /admin/disputes/{id}/resolve | assigned reviewer | `{outcome: UPHELD\|DISMISSED, resolution}` |

### Audit & admin
| GET | /admin/audit-logs?actorId&entityType&entityId&action | ADM | |
| GET | /audit-logs/me | A | own actions, IP omitted |
| GET | /admin/statistics | ADM | |
| GET | /actuator/health | P | |

P* = public for published campaigns; otherwise owner/admin only, 404 for others.
