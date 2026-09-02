import {
  apiClient,
  objectResponseDecoder,
  paginatedResponseDecoder,
} from '../../services/api';
import type {
  PaginationParameters,
  TicketRedemptionResponse,
  TicketResponse,
  TicketValidationResponse,
  ValidateTicketRequest,
} from '../../types';

function requiredString(object: Record<string, unknown>, field: string) {
  const value = object[field];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Expected ${field} to be a non-empty string.`);
  }
  return value;
}

function ticketResponseDecoder(value: unknown): TicketResponse {
  const object = objectResponseDecoder(value);
  const status = requiredString(object, 'status');
  const issuedAt = requiredString(object, 'issuedAt');
  if (!['ACTIVE', 'USED', 'CANCELLED'].includes(status)) {
    throw new Error('Expected a supported ticket status.');
  }
  if (Number.isNaN(Date.parse(issuedAt))) {
    throw new Error('Expected issuedAt to be an ISO date-time.');
  }

  const qrToken =
    object.qrToken === null ? null : requiredString(object, 'qrToken');
  if ((status === 'ACTIVE') !== Boolean(qrToken)) {
    throw new Error('Expected a QR token only for an active ticket.');
  }
  const event = objectResponseDecoder(object.event);
  const venue = objectResponseDecoder(object.venue);
  const seat = objectResponseDecoder(object.seat);

  return {
    id: requiredString(object, 'id'),
    ticketNumber: requiredString(object, 'ticketNumber'),
    bookingId: requiredString(object, 'bookingId'),
    eventSeatId: requiredString(object, 'eventSeatId'),
    qrToken,
    status: status as TicketResponse['status'],
    issuedAt,
    usedAt: object.usedAt === null ? null : requiredString(object, 'usedAt'),
    bookingReference: requiredString(object, 'bookingReference'),
    event: {
      id: requiredString(event, 'id'),
      name: requiredString(event, 'name'),
      startDateTime: requiredString(event, 'startDateTime'),
      endDateTime: requiredString(event, 'endDateTime'),
    },
    venue: {
      id: requiredString(venue, 'id'),
      name: requiredString(venue, 'name'),
      address: requiredString(venue, 'address'),
      city: requiredString(venue, 'city'),
    },
    seat: {
      id: requiredString(seat, 'id'),
      section: requiredString(seat, 'section'),
      row: requiredString(seat, 'row'),
      number: requiredString(seat, 'number'),
      type: requiredString(seat, 'type') as TicketResponse['seat']['type'],
    },
  };
}

function requiredInstant(object: Record<string, unknown>, field: string) {
  const value = requiredString(object, field);
  if (Number.isNaN(Date.parse(value))) {
    throw new Error(`Expected ${field} to be an ISO date-time.`);
  }
  return value;
}

function admissionStatus(object: Record<string, unknown>) {
  const status = requiredString(object, 'status');
  if (!['ACTIVE', 'USED', 'CANCELLED'].includes(status)) {
    throw new Error('Expected a supported admission ticket status.');
  }
  return status as TicketResponse['status'];
}

function validationResponseDecoder(value: unknown): TicketValidationResponse {
  const object = objectResponseDecoder(value);
  return {
    ticketId: requiredString(object, 'ticketId'),
    ticketNumber: requiredString(object, 'ticketNumber'),
    eventId: requiredString(object, 'eventId'),
    eventSeatId: requiredString(object, 'eventSeatId'),
    status: admissionStatus(object),
    eventName: requiredString(object, 'eventName'),
    eventStartDateTime: requiredInstant(object, 'eventStartDateTime'),
  };
}

function redemptionResponseDecoder(value: unknown): TicketRedemptionResponse {
  const object = objectResponseDecoder(value);
  return {
    ticketId: requiredString(object, 'ticketId'),
    ticketNumber: requiredString(object, 'ticketNumber'),
    eventId: requiredString(object, 'eventId'),
    status: admissionStatus(object),
    usedAt: requiredInstant(object, 'usedAt'),
  };
}

export const ticketService = {
  async list(parameters: PaginationParameters) {
    const response = await apiClient.get(
      '/tickets',
      paginatedResponseDecoder(ticketResponseDecoder),
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

  async validate(request: ValidateTicketRequest) {
    const response = await apiClient.post(
      '/tickets/validate',
      request,
      validationResponseDecoder,
    );
    return response.data;
  },

  async redeem(request: ValidateTicketRequest) {
    const response = await apiClient.post(
      '/tickets/redeem',
      request,
      redemptionResponseDecoder,
    );
    return response.data;
  },
};
