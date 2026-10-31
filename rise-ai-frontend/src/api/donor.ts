import { apiRequest } from './client';
import type {
  CampaignResponse, CreateDonationRequest, CreateRefundRequest, DisputeMessageResponse,
  DisputeResponse, DonationResponse, OpenDisputeRequest, PageResponse, RefundResponse,
} from './contracts';

const pageParams = (page: number, size = 10) =>
  new URLSearchParams({ page: String(Math.max(0, Math.floor(page))), size: String(size) }).toString();

export function getCampaignById(id: string) {
  return apiRequest<CampaignResponse>(`/api/v1/campaigns/${encodeURIComponent(id)}`);
}
export function createDonation(campaignId: string, payload: CreateDonationRequest, key: string) {
  return apiRequest<DonationResponse>(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/donations`, {
    method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(payload),
  });
}
export function myDonations(page: number) {
  return apiRequest<PageResponse<DonationResponse>>(`/api/v1/donations/me?${pageParams(page)}`);
}
export function cancelDonation(id: string) {
  return apiRequest<DonationResponse>(`/api/v1/donations/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
}
export function requestRefund(donationId: string, payload: CreateRefundRequest, key: string) {
  return apiRequest<RefundResponse>(`/api/v1/donations/${encodeURIComponent(donationId)}/refunds`, {
    method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(payload),
  });
}
export function myRefunds(page: number) {
  return apiRequest<PageResponse<RefundResponse>>(`/api/v1/refunds/me?${pageParams(page)}`);
}
export function cancelRefund(id: string) {
  return apiRequest<RefundResponse>(`/api/v1/refunds/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
}
export function openDispute(campaignId: string, payload: OpenDisputeRequest) {
  return apiRequest<DisputeResponse>(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/disputes`, {
    method: 'POST', body: JSON.stringify(payload),
  });
}
export function myDisputes(page: number) {
  return apiRequest<PageResponse<DisputeResponse>>(`/api/v1/disputes/me?${pageParams(page)}`);
}
export function getDispute(id: string) {
  return apiRequest<DisputeResponse>(`/api/v1/disputes/${encodeURIComponent(id)}`);
}
export function disputeMessages(id: string, page: number) {
  return apiRequest<PageResponse<DisputeMessageResponse>>(`/api/v1/disputes/${encodeURIComponent(id)}/messages?${pageParams(page)}`);
}
export function addDisputeMessage(id: string, message: string) {
  return apiRequest<DisputeMessageResponse>(`/api/v1/disputes/${encodeURIComponent(id)}/messages`, {
    method: 'POST', body: JSON.stringify({ message }),
  });
}
export function withdrawDispute(id: string) {
  return apiRequest<DisputeResponse>(`/api/v1/disputes/${encodeURIComponent(id)}/withdraw`, { method: 'POST', body: JSON.stringify({ reason: '' }) });
}
