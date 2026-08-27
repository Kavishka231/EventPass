import type { ReactNode } from 'react';

import { SessionContext } from './SessionContext';
import type { SessionState } from './sessionTypes';
import { unauthenticatedSession } from './sessionTypes';

export function SessionProvider({
  children,
  value = unauthenticatedSession,
}: {
  children: ReactNode;
  value?: SessionState;
}) {
  return <SessionContext value={value}>{children}</SessionContext>;
}
