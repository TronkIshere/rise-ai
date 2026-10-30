import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { getMe, login as requestLogin, logout as requestLogout } from '@/api/auth';
import { setAccessToken } from '@/api/client';
import type { UserResponse } from '@/api/contracts';
import { AuthContext, type AuthValue } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);

  useEffect(() => () => setAccessToken(null), []);

  const value = useMemo<AuthValue>(() => ({
    user,
    async login(credentials) {
      const tokens = await requestLogin(credentials);
      if (!tokens.accessToken) throw new Error('Backend không trả access token.');
      setAccessToken(tokens.accessToken);
      try {
        const currentUser = tokens.user ?? await getMe();
        if (!Array.isArray(currentUser.roles)) throw new Error('Thông tin quyền truy cập không hợp lệ.');
        setUser(currentUser);
        setRefreshToken(tokens.refreshToken ?? null);
      } catch (error) {
        setAccessToken(null);
        setUser(null);
        setRefreshToken(null);
        throw error;
      }
    },
    async logout() {
      try { if (refreshToken) await requestLogout(refreshToken); }
      finally {
        setAccessToken(null);
        setRefreshToken(null);
        setUser(null);
      }
    },
  }), [user, refreshToken]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
