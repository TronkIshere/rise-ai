import type { ApiEnvelope, ApiErrorBody } from './contracts';

/** FE-00 intentionally keeps session tokens only in memory, never browser storage. */
let accessToken: string | null = null;
export function setAccessToken(token: string | null): void { accessToken = token; }

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
    public readonly correlationId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type ApiPath =
  | '/api/v1/auth/login'
  | '/api/v1/auth/logout'
  | '/api/v1/auth/me'
  | '/api/v1/campaigns'
  | `/api/v1/campaigns?${string}`
  | `/api/v1/campaigns/slug/${string}`
  | `/api/v1/campaigns/${string}/milestones`
  | `/api/v1/campaigns/${string}/donations`
  | `/api/v1/campaigns/${string}/disputes`
  | `/api/v1/campaigns/${string}`
  | `/api/v1/donations/me?${string}`
  | `/api/v1/donations/${string}/cancel`
  | `/api/v1/donations/${string}/refunds`
  | `/api/v1/refunds/me?${string}`
  | `/api/v1/refunds/${string}/cancel`
  | `/api/v1/disputes/${string}`
  | `/api/v1/disputes/me?${string}`
  | `/api/v1/disputes/${string}/messages?${string}`
  | `/api/v1/disputes/${string}/messages`
  | `/api/v1/disputes/${string}/withdraw`
  | `/api/v1/organizations${string}`
  | `/api/v1/campaigns/mine?${string}`
  | `/api/v1/campaigns/${string}/submit`
  | `/api/v1/campaigns/${string}/publish`
  | `/api/v1/campaigns/${string}/disbursements?${string}`
  | `/api/v1/milestones/${string}`
  | `/api/v1/milestones/${string}/start`
  | `/api/v1/milestones/${string}/evidence${string}`
  | `/api/v1/milestones/${string}/disbursements`
  | `/api/v1/disbursements/${string}/evidence`
  | `/api/v1/disbursements/${string}/resubmit`
  | `/api/v1/disbursements/${string}`
  | `/api/v1/evidence/${string}`
  | `/api/v1/disbursements/${string}/cancel`
  | `/api/v1/admin/${string}`
  | `/api/v1/escrow/${string}`
  | `/api/v1/campaigns/${string}/escrow`
  | `/api/v1/disbursements/${string}/start-review`
  | `/api/v1/disbursements/${string}/request-evidence`
  | `/api/v1/disbursements/${string}/approve`
  | `/api/v1/disbursements/${string}/reject`
  | `/api/v1/disbursements/${string}/release`
  | `/api/v1/evidence/${string}/accept`
  | `/api/v1/evidence/${string}/reject`
  | `/api/v1/campaigns/${string}/approve`
  | `/api/v1/campaigns/${string}/reject`
  | `/api/v1/campaigns/${string}/pause`
  | `/api/v1/campaigns/${string}/resume`
  | `/api/v1/campaigns/${string}/complete`;

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export async function apiRequest<T>(path: ApiPath, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body != null && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', 'Bearer ' + accessToken);

  let response: Response;
  try {
    response = await fetch(baseUrl + path, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Không thể kết nối Backend. Kiểm tra máy chủ và mạng.');
  }

  const text = await response.text();
  let payload: unknown = null;
  if (text) {
    try { payload = JSON.parse(text) as unknown; }
    catch { throw new ApiError(response.status, 'Phản hồi API không phải JSON hợp lệ.'); }
  }
  if (!response.ok) {
    const error = (payload ?? {}) as ApiErrorBody;
    throw new ApiError(response.status, error.message ?? 'Yêu cầu thất bại.', error.code, error.correlationId);
  }
  if (response.status === 204) return undefined as T;
  if (!payload || typeof payload !== 'object' || !('data' in payload)) {
    throw new ApiError(response.status, 'Phản hồi thiếu trường data.');
  }
  return (payload as ApiEnvelope<T>).data as T;
}
