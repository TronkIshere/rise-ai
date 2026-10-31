import { apiRequest } from './client';
import type { CampaignCategory, CampaignResponse, MilestoneResponse, PageResponse } from './contracts';

export interface CampaignFilters {
  q?: string;
  category?: CampaignCategory;
  page?: number;
  size?: number;
}

/** Public discovery always restricts the query to published ACTIVE campaigns. */
export function listActiveCampaigns(filters: CampaignFilters = {}) {
  const params = new URLSearchParams();
  params.set('status', 'ACTIVE');
  params.set('page', String(Math.max(0, Math.floor(filters.page ?? 0))));
  params.set('size', String(Math.min(24, Math.max(1, Math.floor(filters.size ?? 9)))));
  if (filters.q?.trim()) params.set('q', filters.q.trim().slice(0, 120));
  if (filters.category) params.set('category', filters.category);
  const path = ('/api/v1/campaigns?' + params.toString()) as `/api/v1/campaigns?${string}`;
  return apiRequest<PageResponse<CampaignResponse>>(path);
}

export function getCampaignBySlug(slug: string) {
  const path = ('/api/v1/campaigns/slug/' + encodeURIComponent(slug)) as `/api/v1/campaigns/slug/${string}`;
  return apiRequest<CampaignResponse>(path);
}

export function listCampaignMilestones(campaignId: string) {
  const path = ('/api/v1/campaigns/' + encodeURIComponent(campaignId) + '/milestones') as `/api/v1/campaigns/${string}/milestones`;
  return apiRequest<MilestoneResponse[]>(path);
}
