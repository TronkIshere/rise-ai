import { createContext, useContext } from 'react';
import type { LoginRequest, UserResponse } from '@/api/contracts';

export interface AuthValue {
  user: UserResponse | null;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
}
export const AuthContext = createContext<AuthValue | null>(null);
export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth requires AuthProvider');
  return value;
}
