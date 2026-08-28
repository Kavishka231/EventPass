import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useState } from 'react';

import { createEventPassQueryClient } from './queryClient';

export function ServerStateProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createEventPassQueryClient);
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
