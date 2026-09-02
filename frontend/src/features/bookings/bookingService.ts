import {
  apiClient,
  emptyResponseDecoder,
  objectResponseDecoder,
  paginatedResponseDecoder,
} from '../../services/api';
import type {
  BookingResponse,
  CustomerBookingDetails,
  CustomerBookingSummary,
  CreateBookingRequest,
  PaginationParameters,
} from '../../types';

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

export function bookingResponseDecoder(value: unknown): BookingResponse {
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

function nullableString(object: Record<string, unknown>, field: string) {
  const value = object[field];
  if (value === null) return null;
  return requiredString(object, field);
}

function dateString(object: Record<string, unknown>, field: string) {
  const value = requiredString(object, field);
  if (Number.isNaN(Date.parse(value)))
    throw new Error(`Expected ${field} to be an ISO date-time.`);
  return value;
}

function eventDecoder(value: unknown) {
  const object = objectResponseDecoder(value);
  const endDateTime = nullableString(object, 'endDateTime');
  return {
    id: requiredString(object, 'id'),
    name: requiredString(object, 'name'),
    startDateTime: dateString(object, 'startDateTime'),
    endDateTime,
  };
}

function venueDecoder(value: unknown) {
  const object = objectResponseDecoder(value);
  return {
    id: requiredString(object, 'id'),
    name: requiredString(object, 'name'),
    address: nullableString(object, 'address'),
    city: requiredString(object, 'city'),
  };
}

function bookingBase(object: Record<string, unknown>) {
  const status = requiredString(object, 'status');
  if (!statuses.has(status as BookingResponse['status']))
    throw new Error('Expected a supported booking status.');
  if (
    typeof object.totalAmount !== 'number' ||
    !Number.isFinite(object.totalAmount)
  )
    throw new Error('Expected totalAmount to be a number.');
  return {
    id: requiredString(object, 'id'),
    reference: requiredString(object, 'reference'),
    status: status as BookingResponse['status'],
    totalAmount: object.totalAmount,
    currency: requiredString(object, 'currency'),
    createdAt: dateString(object, 'createdAt'),
    event: eventDecoder(object.event),
    venue: venueDecoder(object.venue),
  };
}

function bookingSummaryDecoder(value: unknown): CustomerBookingSummary {
  const object = objectResponseDecoder(value);
  if (
    typeof object.seatCount !== 'number' ||
    !Number.isInteger(object.seatCount)
  )
    throw new Error('Expected seatCount to be an integer.');
  return { ...bookingBase(object), seatCount: object.seatCount };
}

function optionalLifecycle(value: unknown) {
  if (value === null) return null;
  const object = objectResponseDecoder(value);
  const attemptedAt = nullableString(object, 'attemptedAt');
  const completedAt = nullableString(object, 'completedAt');
  return {
    status: requiredString(object, 'status'),
    attemptedAt,
    completedAt,
  };
}

function optionalRefund(value: unknown): CustomerBookingDetails['refund'] {
  if (value === null) return null;
  const object = objectResponseDecoder(value);
  if (typeof object.amount !== 'number' || !Number.isFinite(object.amount)) {
    throw new Error('Expected refund amount to be a number.');
  }
  return { ...optionalLifecycle(value)!, amount: object.amount };
}

function bookingDetailsDecoder(value: unknown): CustomerBookingDetails {
  const object = objectResponseDecoder(value);
  if (!Array.isArray(object.seats))
    throw new Error('Expected seats to be an array.');
  const seats = object.seats.map((value) => {
    const seat = objectResponseDecoder(value);
    if (typeof seat.unitPrice !== 'number')
      throw new Error('Expected seat unitPrice to be a number.');
    return {
      eventSeatId: requiredString(seat, 'eventSeatId'),
      seatId: requiredString(seat, 'seatId'),
      section: requiredString(seat, 'section'),
      row: requiredString(seat, 'row'),
      number: requiredString(seat, 'number'),
      type: requiredString(seat, 'type') as 'REGULAR' | 'PREMIUM' | 'VIP',
      unitPrice: seat.unitPrice,
    };
  });
  return {
    ...bookingBase(object),
    updatedAt: dateString(object, 'updatedAt'),
    expiresAt: dateString(object, 'expiresAt'),
    seats,
    payment: optionalLifecycle(object.payment),
    refund: optionalRefund(object.refund),
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

  async list(parameters: PaginationParameters) {
    const response = await apiClient.get(
      '/bookings',
      paginatedResponseDecoder(bookingSummaryDecoder),
      {
        query: {
          page: parameters.page,
          size: parameters.size,
          sort: parameters.sort,
        },
      },
    );
    return response.data;
  },

  async get(bookingId: string) {
    const response = await apiClient.get(
      `/bookings/${bookingId}`,
      bookingDetailsDecoder,
    );
    return response.data;
  },

  async cancel(bookingId: string) {
    await apiClient.post(
      `/bookings/${bookingId}/cancel`,
      undefined,
      emptyResponseDecoder,
    );
  },
};
