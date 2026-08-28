import { QueryClient } from '@tanstack/react-query';

import { isApiError } from '../api';

const SECOND = 1_000;
const MINUTE = 60 * SECOND;

export function createEventPassQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30 * SECOND,
        gcTime: 5 * MINUTE,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
        retry: (failureCount, error) =>
          isApiError(error) && error.retryable && failureCount < 2,
        retryDelay: (attempt) => Math.min(SECOND * 2 ** attempt, 10 * SECOND),
      },
      mutations: {
        retry: false,
      },
    },
  });
}
