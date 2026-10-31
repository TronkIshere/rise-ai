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
  | `/api/v1/campaigns?${string}`
  | `/api/v1/campaigns/slug/${string}`
  | `/api/v1/campaigns/${string}/milestones`;

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
