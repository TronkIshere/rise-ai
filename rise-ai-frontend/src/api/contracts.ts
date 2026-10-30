/** Typed FE-00 subset verified against the Backend OpenAPI. The Backend contract is authoritative. */
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
export interface CampaignResponse {
  id?: string;
  title?: string;
  slug?: string;
  shortDescription?: string;
  location?: string;
  category?: string;
  campaignStatus?: string;
  targetAmount?: number;
  currency?: string;
  acceptingDonations?: boolean;
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
