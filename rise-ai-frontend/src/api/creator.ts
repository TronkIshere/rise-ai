import { apiRequest } from './client';
import type {
  CampaignRequest, CampaignResponse, CreateDisbursementRequest, DisbursementResponse, EvidenceRequest, EvidenceResponse,
  MilestoneRequest, MilestoneResponse, OrganizationRequest, OrganizationResponse, PageResponse,
} from './contracts';
const ident = (id: string) => encodeURIComponent(id);
const page = (n: number) => new URLSearchParams({ page: String(n), size: '10' }).toString();
export const myOrganizations = (n=0) => apiRequest<PageResponse<OrganizationResponse>>(`/api/v1/organizations/mine?${page(n)}`);
export const createOrganization = (data: OrganizationRequest) => apiRequest<OrganizationResponse>('/api/v1/organizations', {method:'POST',body:JSON.stringify(data)});
export const updateOrganization = (id:string,data: OrganizationRequest) => apiRequest<OrganizationResponse>(`/api/v1/organizations/${ident(id)}`, {method:'PUT',body:JSON.stringify(data)});
export const requestOrganizationVerification = (id:string) => apiRequest<OrganizationResponse>(`/api/v1/organizations/${ident(id)}/submit-verification`,{method:'POST'});
export const myCreatorCampaigns = (n=0) => apiRequest<PageResponse<CampaignResponse>>(`/api/v1/campaigns/mine?${page(n)}`);
export const getCreatorCampaign = (id:string) => apiRequest<CampaignResponse>(`/api/v1/campaigns/${ident(id)}`);
export const createCreatorCampaign = (data:CampaignRequest) => apiRequest<CampaignResponse>('/api/v1/campaigns', {method:'POST',body:JSON.stringify(data)});
export const updateCreatorCampaign = (id:string,data:CampaignRequest) => apiRequest<CampaignResponse>(`/api/v1/campaigns/${ident(id)}`,{method:'PUT',body:JSON.stringify(data)});
export const submitCreatorCampaign = (id:string) => apiRequest<CampaignResponse>(`/api/v1/campaigns/${ident(id)}/submit`,{method:'POST'});
export const publishCreatorCampaign = (id:string) => apiRequest<CampaignResponse>(`/api/v1/campaigns/${ident(id)}/publish`,{method:'POST'});
export const createCreatorMilestone = (id:string,data:MilestoneRequest) => apiRequest<MilestoneResponse>(`/api/v1/campaigns/${ident(id)}/milestones`,{method:'POST',body:JSON.stringify(data)});
export const updateCreatorMilestone = (id:string,data:MilestoneRequest) => apiRequest<MilestoneResponse>(`/api/v1/milestones/${ident(id)}`,{method:'PUT',body:JSON.stringify(data)});
export const startCreatorMilestone = (id:string) => apiRequest<MilestoneResponse>(`/api/v1/milestones/${ident(id)}/start`,{method:'POST'});
export const creatorEvidence = (milestoneId:string,n=0) => apiRequest<PageResponse<EvidenceResponse>>(`/api/v1/milestones/${ident(milestoneId)}/evidence?${page(n)}`);
export const createCreatorEvidence = (milestoneId:string,data:EvidenceRequest) => apiRequest<EvidenceResponse>(`/api/v1/milestones/${ident(milestoneId)}/evidence`,{method:'POST',body:JSON.stringify(data)});
export const creatorDisbursements = (campaignId:string,n=0) => apiRequest<PageResponse<DisbursementResponse>>(`/api/v1/campaigns/${ident(campaignId)}/disbursements?${page(n)}`);
export const createCreatorDisbursement = (milestoneId:string,data:CreateDisbursementRequest,key:string) => apiRequest<DisbursementResponse>(`/api/v1/milestones/${ident(milestoneId)}/disbursements`,{method:'POST',headers:{'Idempotency-Key':key},body:JSON.stringify(data)});
export const resubmitCreatorDisbursement = (id:string) => apiRequest<DisbursementResponse>(`/api/v1/disbursements/${ident(id)}/resubmit`,{method:'POST',body:JSON.stringify({reason:''})});
export const cancelCreatorDisbursement = (id:string,reason:string) => apiRequest<DisbursementResponse>(`/api/v1/disbursements/${ident(id)}/cancel`,{method:'POST',body:JSON.stringify({reason})});
