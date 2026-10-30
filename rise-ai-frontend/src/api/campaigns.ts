import { apiRequest } from './client';
import type { CampaignResponse, PageResponse } from './contracts';

export function listActiveCampaigns() {
  const params = new URLSearchParams({ status: 'ACTIVE', page: '0', size: '9' });
  return apiRequest<PageResponse<CampaignResponse>>('/api/v1/campaigns?' + params.toString() as `/api/v1/campaigns?${string}`);
}
export function getCampaignBySlug(slug: string) {
  return apiRequest<CampaignResponse>('/api/v1/campaigns/slug/' + encodeURIComponent(slug) as `/api/v1/campaigns/slug/${string}`);
}
