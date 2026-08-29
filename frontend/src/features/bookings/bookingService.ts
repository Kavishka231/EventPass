import { apiClient, objectResponseDecoder } from '../../services/api';
import type { BookingResponse, CreateBookingRequest } from '../../types';

const statuses = new Set<BookingResponse['status']>([
  'PENDING',
  'CONFIRMED',
  'CANCELLED',
  'EXPIRED',
  'FAILED',
]);

function requiredString(object: Record<string, unknown>, field: string) {
  const value = object[field];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Expected ${field} to be a non-empty string.`);
  }
  return value;
}

function bookingResponseDecoder(value: unknown): BookingResponse {
  const object = objectResponseDecoder(value);
  const status = requiredString(object, 'status');
  const totalAmount = object.totalAmount;
  if (!statuses.has(status as BookingResponse['status'])) {
    throw new Error('Expected a supported booking status.');
  }
  if (
    typeof totalAmount !== 'number' ||
    !Number.isFinite(totalAmount) ||
    totalAmount < 0
  ) {
    throw new Error('Expected totalAmount to be a non-negative number.');
  }
  if (!Array.isArray(object.eventSeatIds)) {
    throw new Error('Expected eventSeatIds to be an array.');
  }
  const eventSeatIds = object.eventSeatIds.map((item) => {
    if (typeof item !== 'string' || item.length === 0) {
      throw new Error('Expected every event seat ID to be a string.');
    }
    return item;
  });
  const createdAt = requiredString(object, 'createdAt');
  if (Number.isNaN(Date.parse(createdAt))) {
    throw new Error('Expected createdAt to be an ISO date-time.');
  }

  return {
    id: requiredString(object, 'id'),
    reference: requiredString(object, 'reference'),
    eventId: requiredString(object, 'eventId'),
    status: status as BookingResponse['status'],
    totalAmount,
    currency: requiredString(object, 'currency'),
    eventSeatIds,
    createdAt,
  };
}

export const bookingService = {
  async create(request: CreateBookingRequest, idempotencyKey: string) {
    const response = await apiClient.post(
      '/bookings',
      request,
      bookingResponseDecoder,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return response.data;
  },
};
