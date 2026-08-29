import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../services/query';
import type { EventSearchParameters } from '../../types';
import { eventService } from './eventService';

export function useEvents(parameters: EventSearchParameters) {
  return useQuery({
    queryKey: queryKeys.events.list(parameters),
    queryFn: () => eventService.list(parameters),
    placeholderData: keepPreviousData,
  });
}
