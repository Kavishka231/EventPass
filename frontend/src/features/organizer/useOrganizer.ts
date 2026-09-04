import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { queryKeys } from '../../services/query';
import type {
  EventRequest,
  EventSeatConfigurationRequest,
  PaginationParameters,
} from '../../types';
import { organizerService } from './organizerService';

export const useOrganizerEvents = (parameters: PaginationParameters) =>
  useQuery({
    queryKey: queryKeys.organizer.events(parameters),
    queryFn: () => organizerService.listEvents(parameters),
    placeholderData: keepPreviousData,
  });
export const useOrganizerEvent = (id: string) =>
  useQuery({
    queryKey: queryKeys.organizer.event(id),
    queryFn: () => organizerService.getEvent(id),
    enabled: Boolean(id),
  });
export const useOrganizerVenues = () =>
  useQuery({
    queryKey: queryKeys.venues.list({ page: 0, size: 100 }),
    queryFn: () =>
      organizerService.listVenues({ page: 0, size: 100, sort: 'name,asc' }),
  });
export const useOrganizerInventory = (id: string) =>
  useQuery({
    queryKey: queryKeys.organizer.inventory(id),
    queryFn: () => organizerService.inventory(id),
    enabled: Boolean(id),
  });
export const useOrganizerBookings = (
  id: string,
  parameters: PaginationParameters,
) =>
  useQuery({
    queryKey: queryKeys.organizer.eventBookings(id, parameters),
    queryFn: () => organizerService.bookings(id, parameters),
    enabled: Boolean(id),
    placeholderData: keepPreviousData,
  });

export function useEventMutation(id?: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: EventRequest) =>
      id
        ? organizerService.updateEvent(id, request)
        : organizerService.createEvent(request),
    onSuccess: async (event) => {
      await client.invalidateQueries({ queryKey: queryKeys.organizer.all });
      client.setQueryData(queryKeys.organizer.event(event.id), event);
    },
  });
}
export function useCancelEvent() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => organizerService.cancelEvent(id),
    onSuccess: () =>
      client.invalidateQueries({ queryKey: queryKeys.organizer.all }),
  });
}
export function useConfigureInventory(id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: EventSeatConfigurationRequest[]) =>
      organizerService.configureInventory(id, request),
    onSuccess: () =>
      client.invalidateQueries({ queryKey: queryKeys.organizer.inventory(id) }),
  });
}
