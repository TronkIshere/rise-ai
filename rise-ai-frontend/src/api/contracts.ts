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
  plannedAmount?: number;
  releasedAmount?: number;
  dueAt?: string;
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
