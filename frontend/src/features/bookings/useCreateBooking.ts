import { useMutation, useQueryClient } from '@tanstack/react-query';

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
