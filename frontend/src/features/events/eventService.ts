import {
  apiClient,
  objectResponseDecoder,
  paginatedResponseDecoder,
} from '../../services/api';
import type {
  EventResponse,
  EventSearchParameters,
  EventSeatResponse,
} from '../../types';

function requiredString(object: Record<string, unknown>, field: string) {
  const value = object[field];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Expected ${field} to be a non-empty string.`);
  }
  return value;
}

function requiredInstant(object: Record<string, unknown>, field: string) {
  const value = requiredString(object, field);
  if (Number.isNaN(Date.parse(value))) {
    throw new Error(`Expected ${field} to be an ISO date-time.`);
  }
  return value;
}

function eventResponseDecoder(value: unknown): EventResponse {
  const object = objectResponseDecoder(value);
  const status = requiredString(object, 'status');
  if (status !== 'PUBLISHED') {
    throw new Error('Public event discovery returned a non-published event.');
  }

  return {
    id: requiredString(object, 'id'),
    name: requiredString(object, 'name'),
    description: requiredString(object, 'description'),
    category: requiredString(object, 'category'),
    startDateTime: requiredInstant(object, 'startDateTime'),
    endDateTime: requiredInstant(object, 'endDateTime'),
    status,
    venueId: requiredString(object, 'venueId'),
    venueName: requiredString(object, 'venueName'),
    city: requiredString(object, 'city'),
  };
}

function seatResponseDecoder(value: unknown): EventSeatResponse {
  const object = objectResponseDecoder(value);
  const type = requiredString(object, 'type');
  const availability = requiredString(object, 'availability');
  const price = object.price;
  if (!['REGULAR', 'PREMIUM', 'VIP'].includes(type)) {
    throw new Error('Expected a supported seat type.');
  }
  if (!['AVAILABLE', 'HELD', 'SOLD', 'BLOCKED'].includes(availability)) {
    throw new Error('Expected a supported seat availability.');
  }
  if (typeof price !== 'number' || !Number.isFinite(price) || price < 0) {
    throw new Error('Expected price to be a non-negative number.');
  }

  return {
    id: requiredString(object, 'id'),
    section: requiredString(object, 'section'),
    row: requiredString(object, 'row'),
    number: requiredString(object, 'number'),
    type: type as EventSeatResponse['type'],
    price,
    availability: availability as EventSeatResponse['availability'],
  };
}

function seatListDecoder(value: unknown): EventSeatResponse[] {
  if (!Array.isArray(value))
    throw new Error('Expected seat inventory to be an array.');
  return value.map(seatResponseDecoder);
}

export const eventService = {
  async list(parameters: EventSearchParameters) {
    const response = await apiClient.get(
      '/events',
      paginatedResponseDecoder<EventResponse>(eventResponseDecoder),
      {
        authentication: 'omit',
        query: {
          category: parameters.category,
          city: parameters.city,
          startDate: parameters.startDate,
          endDate: parameters.endDate,
          page: parameters.page,
          size: parameters.size,
          sort: parameters.sort,
        },
      },
    );
    return response.data;
  },

  async get(eventId: string) {
    const response = await apiClient.get(
      `/events/${eventId}`,
      eventResponseDecoder,
      {
        authentication: 'omit',
      },
    );
    return response.data;
  },

  async seats(eventId: string) {
    const response = await apiClient.get(
      `/events/${eventId}/seats`,
      seatListDecoder,
      { authentication: 'omit' },
    );
    return response.data;
  },
};
