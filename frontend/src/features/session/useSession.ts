import { use } from 'react';

import { SessionContext } from './SessionContext';

export function useSession() {
  return use(SessionContext);
}
