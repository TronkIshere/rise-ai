/** FE-01 public API subset. Source of truth: Backend docs/api/openapi-v1.json. */
export type UserRole = 'ADMIN' | 'DONOR' | 'CAMPAIGN_CREATOR';
export interface UserResponse {
  id?: string;
  email?: string;
  fullName?: string;
  roles?: UserRole[];
  status?: 'ACTIVE' | 'SUSPENDED';
}
export interface TokenResponse {
  accessToken?: string;
  expiresIn?: number;
  refreshToken?: string;
  user?: UserResponse;
}
export interface LoginRequest { email: string; password: string }

export type CampaignCategory =
  | 'EDUCATION' | 'COMMUNITY' | 'FOOD_SECURITY' | 'WATER_SANITATION'
  | 'SCHOLARSHIP' | 'INFRASTRUCTURE' | 'HEALTH' | 'OTHER';

export interface FundingSummary {
  availableBalance?: number;
  netRaised?: number;
  progressPercent?: number;
  totalConfirmed?: number;
  totalDisbursed?: number;
  totalRefunded?: number;
  totalReserved?: number;
}
export interface OrganizationSummary {
  id?: string;
  name?: string;
  slug?: string;
  verificationStatus?: string;
}
export interface CampaignResponse {
  id?: string;
  creatorId?: string;
  title?: string;
  slug?: string;
  shortDescription?: string;
  fullDescription?: string;
  problemStatement?: string;
  solutionDescription?: string;
  impactGoals?: string;
  location?: string;
  category?: CampaignCategory;
  campaignStatus?: string;
  targetAmount?: number;
  minimumDonationAmount?: number;
  currency?: string;
  acceptingDonations?: boolean;
  coverImageUrl?: string;
  beneficiaryCount?: number;
  beneficiaryDescription?: string;
  startAt?: string;
  budgetBreakdown?: BudgetItem[];
  galleryImageUrls?: string[];
  reviewStatus?: string;
  reviewNote?: string;
  publishedAt?: string;
  endAt?: string;
  funding?: FundingSummary;
  organization?: OrganizationSummary;
}
export interface MilestoneResponse {
  id?: string;
  title?: string;
  description?: string;
  milestoneStatus?: string;
  sequenceNumber?: number;
  evidenceCount?: number;
  plannedAmount?: number;
  releasedAmount?: number;
  dueAt?: string;
  startAt?: string;
  releaseConditions?: string;
  remainingAmount?: number;
  expectedDeliverables?: string;
}
export interface PageResponse<T> {
  data?: T[];
  currentPage?: number;
  pageSize?: number;
  totalElements?: number;
  totalPages?: number;
}
export interface ApiEnvelope<T> { code?: number; message?: string; data?: T }
export interface ApiErrorBody {
  status?: number;
  code?: string;
  message?: string;
  correlationId?: string;
}

export type DonationStatus = 'PENDING' | 'CONFIRMED' | 'FAILED' | 'CANCELLED' | 'REFUND_PENDING' | 'PARTIALLY_REFUNDED' | 'REFUNDED';
export type PaymentMethod = 'SIMULATED_BANK_TRANSFER' | 'SIMULATED_CARD' | 'SIMULATED_E_WALLET';
export interface CreateDonationRequest { amount: number; anonymous: boolean; currency: string; donorMessage?: string; paymentMethod: PaymentMethod }
export interface DonationResponse {
  id?: string; donationCode?: string; campaignId?: string; donorId?: string;
  amount?: number; currency?: string; donationStatus?: DonationStatus;
  donorMessage?: string; paymentMethod?: PaymentMethod; anonymous?: boolean;
  refundedAmount?: number; createdAt?: string; confirmedAt?: string; simulatedReference?: string;
}
export type RefundStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'RECORDED';
export interface CreateRefundRequest { amount: number; reason: string }
export interface RefundResponse {
  id?: string; refundCode?: string; donationId?: string; campaignId?: string;
  amount?: number; currency?: string; refundStatus?: RefundStatus; reason?: string;
  decisionReason?: string; createdAt?: string; recordedAt?: string;
  externalPaymentStatus?: 'NOT_EXECUTED_SIMULATION';
}
export type DisputeType = 'MISUSE_OF_FUNDS' | 'MISSING_EVIDENCE' | 'MISLEADING_INFORMATION' | 'DELIVERY_NOT_RECEIVED' | 'OTHER';
export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED_UPHELD' | 'RESOLVED_DISMISSED' | 'WITHDRAWN';
export interface OpenDisputeRequest { disputeType: DisputeType; title: string; description: string; supportingReferences?: string[] }
export interface DisputeResponse {
  id?: string; disputeCode?: string; campaignId?: string; openedBy?: string;
  disputeType?: DisputeType; disputeStatus?: DisputeStatus; title?: string;
  description?: string; resolution?: string; createdAt?: string; updatedAt?: string;
}
export interface DisputeMessageResponse { id?: string; disputeId?: string; authorId?: string; message?: string; createdAt?: string }

export type OrganizationType = 'NGO' | 'CHARITY_FUND' | 'COMMUNITY_GROUP' | 'SOCIAL_ENTERPRISE' | 'EDUCATIONAL_INSTITUTION' | 'OTHER';
export type VerificationStatus = 'UNVERIFIED' | 'PENDING_REVIEW' | 'VERIFIED' | 'REJECTED';
export interface OrganizationRequest {
  name: string; organizationType: OrganizationType; contactEmail: string;
  contactPhone?: string; address?: string; description?: string;
}
export interface OrganizationResponse extends OrganizationRequest {
  id?: string; createdBy?: string; slug?: string; verificationStatus?: VerificationStatus;
  verificationNotes?: string; verificationDisclaimer?: string; createdAt?: string;
}
export interface BudgetItem { item: string; amount: number; note?: string }
export interface CampaignRequest {
  organizationId: string; title: string; shortDescription: string; fullDescription: string;
  category: CampaignCategory; beneficiaryDescription: string; beneficiaryCount: number;
  targetAmount: number; minimumDonationAmount: number; startAt: string; endAt: string;
  location: string; problemStatement: string; solutionDescription: string; impactGoals: string;
  currency?: string; coverImageUrl?: string; galleryImageUrls?: string[]; budgetBreakdown?: BudgetItem[];
}
export interface MilestoneRequest {
  title: string; description?: string; startAt: string; dueAt: string;
  plannedAmount: number; expectedDeliverables: string; releaseConditions: string;
}
export type EvidenceType = 'RECEIPT' | 'INVOICE' | 'DELIVERY_RECORD' | 'PHOTO' | 'PROGRESS_REPORT' | 'ACTIVITY_DESCRIPTION' | 'EXTERNAL_REFERENCE';
export interface EvidenceRequest {
  title: string; evidenceType: EvidenceType; description?: string; fileUrl?: string;
  fileHash?: string; externalReference?: string;
}
export interface EvidenceResponse {
  id?: string; title?: string; evidenceType?: EvidenceType; evidenceStatus?: 'SUBMITTED'|'ACCEPTED'|'REJECTED';
  milestoneId?: string; disbursementRequestId?: string; description?: string; fileUrl?: string;
  fileHash?: string; externalReference?: string; reviewNote?: string; submittedAt?: string;
}
export type DisbursementStatus = 'SUBMITTED'|'UNDER_REVIEW'|'NEEDS_MORE_EVIDENCE'|'APPROVED'|'REJECTED'|'RELEASED'|'CANCELLED'|'DISPUTED';
export interface CreateDisbursementRequest { amount: number; reason: string }
export interface DisbursementResponse {
  id?: string; requestCode?: string; milestoneId?: string; campaignId?: string;
  requestedAmount?: number; currency?: string; requestReason?: string; requestStatus?: DisbursementStatus;
  decisionReason?: string; createdAt?: string; submittedAt?: string; simulated?: boolean;
}
