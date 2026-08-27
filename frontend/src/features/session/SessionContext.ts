import { createContext } from 'react';

import type { SessionState } from './sessionTypes';
import { unauthenticatedSession } from './sessionTypes';

export const SessionContext = createContext<SessionState>(
  unauthenticatedSession,
);
