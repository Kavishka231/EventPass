import {
  apiClient,
  objectResponseDecoder,
  paginatedResponseDecoder,
} from '../../services/api';
import type { PaginationParameters, TicketResponse } from '../../types';

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

  return {
    id: requiredString(object, 'id'),
    ticketNumber: requiredString(object, 'ticketNumber'),
    bookingId: requiredString(object, 'bookingId'),
    eventSeatId: requiredString(object, 'eventSeatId'),
    qrToken: requiredString(object, 'qrToken'),
    status: status as TicketResponse['status'],
    issuedAt,
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
};
