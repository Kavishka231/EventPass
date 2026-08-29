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

export function useEvent(eventId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.events.detail(eventId),
    queryFn: () => eventService.get(eventId),
    enabled,
    staleTime: 60_000,
  });
}

export function useEventSeats(eventId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.events.seats(eventId),
    queryFn: () => eventService.seats(eventId),
    enabled,
    staleTime: 10_000,
    refetchInterval: 15_000,
    refetchIntervalInBackground: false,
  });
}
