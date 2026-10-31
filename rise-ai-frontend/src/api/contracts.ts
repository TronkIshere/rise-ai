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
