export type UserRole = 'CUSTOMER' | 'ORGANIZER' | 'ADMIN';
export type SessionStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface UserSession {
  userId: string;
  displayName: string;
  role: UserRole;
}

export interface SessionState {
  status: SessionStatus;
  session: UserSession | null;
}

export const unauthenticatedSession: SessionState = {
  status: 'unauthenticated',
  session: null,
};
