import { useQueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { authService } from '../auth';
import { apiClient } from '../../services/api';
import type { AuthResponse } from '../../types';
import { SessionContext } from './SessionContext';
import { credentialVault } from './credentialVault';
import { sessionFromAuthentication } from './jwtClaims';
import type { SessionState } from './sessionTypes';

const initializingState: SessionState = {
  status: 'initializing',
  session: null,
  sessionExpired: false,
};

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<SessionState>(initializingState);
  const refreshFlight = useRef<Promise<boolean> | null>(null);
  const sessionRevision = useRef(0);

  const clearSession = useCallback(
    (sessionExpired = false) => {
      sessionRevision.current += 1;
      credentialVault.clear();
      queryClient.clear();
      setState({ status: 'unauthenticated', session: null, sessionExpired });
    },
    [queryClient],
  );

  const authenticate = useCallback(
    (response: AuthResponse) => {
      const session = sessionFromAuthentication(response);
      sessionRevision.current += 1;
      credentialVault.replace(response);
      queryClient.clear();
      setState({ status: 'authenticated', session, sessionExpired: false });
      return Promise.resolve();
    },
    [queryClient],
  );

  const refresh = useCallback(() => {
    if (refreshFlight.current) return refreshFlight.current;
    const current = credentialVault.read();
    if (!current) {
      clearSession();
      return Promise.resolve(false);
    }

    setState((existing) => ({ ...existing, status: 'refreshing' }));
    const startingRevision = sessionRevision.current;
    const flight = authService
      .refresh(current.refreshToken)
      .then(async (response) => {
        if (sessionRevision.current !== startingRevision) return false;
        await authenticate(response);
        return true;
      })
      .catch(() => {
        clearSession(true);
        return false;
      })
      .finally(() => {
        refreshFlight.current = null;
      });
    refreshFlight.current = flight;
    return flight;
  }, [authenticate, clearSession]);

  const logout = useCallback(async () => {
    const current = credentialVault.read();
    try {
      if (current) await authService.logout(current.refreshToken);
    } finally {
      clearSession();
    }
  }, [clearSession]);

  useEffect(() => {
    const current = credentialVault.read();
    if (!current) {
      setState({
        status: 'unauthenticated',
        session: null,
        sessionExpired: false,
      });
      return;
    }
    void refresh();
  }, [refresh]);

  useEffect(() => {
    apiClient.setAuthenticationTransport({
      getAccessToken: () => credentialVault.read()?.accessToken ?? null,
      onUnauthorized: () => refresh(),
    });
    return () => apiClient.setAuthenticationTransport(undefined);
  }, [refresh]);

  const value = useMemo(
    () => ({ ...state, authenticate, logout }),
    [authenticate, logout, state],
  );

  return <SessionContext value={value}>{children}</SessionContext>;
}
