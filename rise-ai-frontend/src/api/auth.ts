import { apiRequest } from './client';
import type { LoginRequest, TokenResponse, UserResponse } from './contracts';

export function login(credentials: LoginRequest) {
  return apiRequest<TokenResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  });
}
export function getMe() {
  return apiRequest<UserResponse>('/api/v1/auth/me');
}
export function logout(refreshToken: string) {
  return apiRequest<unknown>('/api/v1/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
}
