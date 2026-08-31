import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import { queryKeys } from '../../services/query';
import type { CreateBookingRequest } from '../../types';
import { bookingService } from './bookingService';

interface BookingAttempt {
  request: CreateBookingRequest;
  idempotencyKey: string;
}

export function useCreateBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ idempotencyKey, request }: BookingAttempt) =>
      bookingService.create(request, idempotencyKey),
    retry: false,
    onSuccess: async (booking) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.bookings.all }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.events.seats(booking.eventId),
        }),
        queryClient.invalidateQueries({ queryKey: queryKeys.events.lists() }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.events.detail(booking.eventId),
        }),
      ]);
    },
  });
}

export function useBookings(page: number, size: number) {
  const parameters = { page, size, sort: 'createdAt,desc' } as const;
  return useQuery({
    queryKey: queryKeys.bookings.list(parameters),
    queryFn: () => bookingService.list(parameters),
    placeholderData: keepPreviousData,
  });
}

export function useBooking(bookingId: string) {
  return useQuery({
    queryKey: queryKeys.bookings.detail(bookingId),
    queryFn: () => bookingService.get(bookingId),
    enabled: Boolean(bookingId),
  });
}

export function useCancelBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bookingId: string) => bookingService.cancel(bookingId),
    retry: false,
    onSuccess: async (_, bookingId) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.bookings.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.tickets.all }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.bookings.detail(bookingId),
        }),
        queryClient.invalidateQueries({ queryKey: queryKeys.events.all }),
      ]);
    },
  });
}
