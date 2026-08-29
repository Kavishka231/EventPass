import { createContext } from 'react';

import type { SessionContextValue } from './sessionTypes';
import { unauthenticatedSession } from './sessionTypes';

export const SessionContext = createContext<SessionContextValue>({
  ...unauthenticatedSession,
  authenticate: () => Promise.resolve(),
  logout: () => Promise.resolve(),
});
