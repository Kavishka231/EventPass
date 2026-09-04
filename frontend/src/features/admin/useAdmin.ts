import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { queryKeys } from '../../services/query';
import type {
  AdminBookingSearchParameters,
  CreateSeatRequest,
  EventRequest,
  PaginationParameters,
  UpdateUserRequest,
  VenueRequest,
} from '../../types';
import { adminService } from './adminService';
export const useAdminStatistics = () =>
  useQuery({
    queryKey: queryKeys.admin.statistics(),
    queryFn: () => adminService.statistics(),
  });
export const useAdminUsers = (p: PaginationParameters) =>
  useQuery({
    queryKey: queryKeys.admin.users(p),
    queryFn: () => adminService.users(p),
    placeholderData: keepPreviousData,
  });
export const useAdminVenues = (p: PaginationParameters) =>
  useQuery({
    queryKey: queryKeys.venues.list(p),
    queryFn: () => adminService.venues(p),
    placeholderData: keepPreviousData,
  });
export const useAdminVenue = (id: string) =>
  useQuery({
    queryKey: queryKeys.venues.detail(id),
    queryFn: () => adminService.venue(id),
    enabled: Boolean(id),
  });
export const useAdminSeats = (id: string, p: PaginationParameters) =>
  useQuery({
    queryKey: [...queryKeys.venues.detail(id), 'seats', p],
    queryFn: () => adminService.seats(id, p),
    enabled: Boolean(id),
    placeholderData: keepPreviousData,
  });
export const useAdminEvents = (p: PaginationParameters) =>
  useQuery({
    queryKey: [...queryKeys.admin.all, 'events', p],
    queryFn: () => adminService.events(p),
    placeholderData: keepPreviousData,
  });
export const useAdminEvent = (id: string) =>
  useQuery({
    queryKey: [...queryKeys.admin.all, 'event', id],
    queryFn: () => adminService.event(id),
    enabled: Boolean(id),
  });
export const useAdminBookings = (p: AdminBookingSearchParameters) =>
  useQuery({
    queryKey: queryKeys.admin.bookings(p),
    queryFn: () => adminService.bookings(p),
    placeholderData: keepPreviousData,
  });
export const useAdminBooking = (id: string) =>
  useQuery({
    queryKey: [...queryKeys.admin.all, 'booking', id],
    queryFn: () => adminService.booking(id),
    enabled: Boolean(id),
  });
const invalidate = (
  client: ReturnType<typeof useQueryClient>,
  key: readonly unknown[],
) => client.invalidateQueries({ queryKey: key });
export function useUpdateUser() {
  const c = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateUserRequest }) =>
      adminService.updateUser(id, request),
    onSuccess: () => invalidate(c, queryKeys.admin.all),
  });
}
export function useVenueMutation(id?: string) {
  const c = useQueryClient();
  return useMutation({
    mutationFn: (r: VenueRequest) =>
      id ? adminService.updateVenue(id, r) : adminService.createVenue(r),
    onSuccess: () => invalidate(c, queryKeys.venues.all),
  });
}
export function useDeleteVenue() {
  const c = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminService.deleteVenue(id),
    onSuccess: () => invalidate(c, queryKeys.venues.all),
  });
}
export function useCreateSeats(id: string) {
  const c = useQueryClient();
  return useMutation({
    mutationFn: (r: CreateSeatRequest[]) => adminService.createSeats(id, r),
    onSuccess: () => invalidate(c, queryKeys.venues.detail(id)),
  });
}
export function useUpdateAdminEvent(id: string) {
  const c = useQueryClient();
  return useMutation({
    mutationFn: (r: EventRequest) => adminService.updateEvent(id, r),
    onSuccess: () => invalidate(c, queryKeys.admin.all),
  });
}
export function useCancelAdminEvent() {
  const c = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminService.cancelEvent(id),
    onSuccess: () => invalidate(c, queryKeys.admin.all),
  });
}
export function useCancelAdminBooking() {
  const c = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminService.cancelBooking(id),
    onSuccess: () => invalidate(c, queryKeys.admin.all),
  });
}
