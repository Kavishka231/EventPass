import type { UserRole } from '../../types/shared';

export type { UserRole };
export type SessionStatus =
  'initializing' | 'authenticated' | 'unauthenticated' | 'refreshing';

export interface UserSession {
  userId: string;
  role: UserRole;
}

export interface SessionState {
  status: SessionStatus;
  session: UserSession | null;
  sessionExpired: boolean;
}

export interface SessionContextValue extends SessionState {
  authenticate(response: import('../../types').AuthResponse): Promise<void>;
  logout(): Promise<void>;
}

export const unauthenticatedSession: SessionState = {
  status: 'unauthenticated',
  session: null,
  sessionExpired: false,
};
