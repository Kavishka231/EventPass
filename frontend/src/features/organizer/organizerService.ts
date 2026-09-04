import {
  apiClient,
  emptyResponseDecoder,
  objectResponseDecoder,
  paginatedResponseDecoder,
  unknownResponseDecoder,
} from '../../services/api';
import type {
  EventRequest,
  EventResponse,
  EventSeatConfigurationRequest,
  OrganizerEventBookingResponse,
  OrganizerInventoryOption,
  PaginatedResponse,
  PaginationParameters,
  VenueResponse,
} from '../../types';

const text = (value: Record<string, unknown>, field: string) => {
  const result = value[field];
  if (typeof result !== 'string' || !result)
    throw new Error(`Expected ${field}.`);
  return result;
};
const number = (value: Record<string, unknown>, field: string) => {
  const result = value[field];
  if (typeof result !== 'number' || !Number.isFinite(result))
    throw new Error(`Expected ${field}.`);
  return result;
};

export function decodeOrganizerEvent(value: unknown): EventResponse {
  const item = objectResponseDecoder(value);
  return {
    id: text(item, 'id'),
    name: text(item, 'name'),
    description: text(item, 'description'),
    category: text(item, 'category'),
    startDateTime: text(item, 'startDateTime'),
    endDateTime: text(item, 'endDateTime'),
    venueId: text(item, 'venueId'),
    venueName: text(item, 'venueName'),
    city: text(item, 'city'),
    status: text(item, 'status') as EventResponse['status'],
  };
}
const decodeVenue = (value: unknown): VenueResponse => {
  const item = objectResponseDecoder(value);
  return {
    id: text(item, 'id'),
    name: text(item, 'name'),
    address: text(item, 'address'),
    city: text(item, 'city'),
    capacity: number(item, 'capacity'),
  };
};
const decodeInventory = (value: unknown): OrganizerInventoryOption[] => {
  if (!Array.isArray(value)) throw new Error('Expected inventory options.');
  return value.map((entry) => {
    const item = objectResponseDecoder(entry);
    const price = item.price;
    return {
      seatId: text(item, 'seatId'),
      section: text(item, 'section'),
      row: text(item, 'row'),
      number: text(item, 'number'),
      type: text(item, 'type') as OrganizerInventoryOption['type'],
      price: typeof price === 'number' ? price : null,
      blocked: item.blocked === true,
      configured: item.configured === true,
    };
  });
};
const decodeBooking = (value: unknown): OrganizerEventBookingResponse => {
  const item = objectResponseDecoder(value);
  if (!Array.isArray(item.eventSeatIds))
    throw new Error('Expected eventSeatIds.');
  return {
    id: text(item, 'id'),
    reference: text(item, 'reference'),
    eventId: text(item, 'eventId'),
    customerId: text(item, 'customerId'),
    customerEmail: text(item, 'customerEmail'),
    customerFirstName: text(item, 'customerFirstName'),
    customerLastName: text(item, 'customerLastName'),
    status: text(item, 'status') as OrganizerEventBookingResponse['status'],
    totalAmount: number(item, 'totalAmount'),
    currency: text(item, 'currency'),
    eventSeatIds: item.eventSeatIds.map(String),
    createdAt: text(item, 'createdAt'),
  };
};

const query = (parameters: PaginationParameters) => ({
  page: parameters.page,
  size: parameters.size,
  sort: parameters.sort,
});
export const organizerService = {
  async listEvents(
    parameters: PaginationParameters,
  ): Promise<PaginatedResponse<EventResponse>> {
    return (
      await apiClient.get(
        '/organizer/events',
        paginatedResponseDecoder(decodeOrganizerEvent),
        { query: query(parameters) },
      )
    ).data;
  },
  async getEvent(id: string) {
    return (
      await apiClient.get(`/organizer/events/${id}`, decodeOrganizerEvent)
    ).data;
  },
  async createEvent(request: EventRequest) {
    return (await apiClient.post('/events', request, decodeOrganizerEvent))
      .data;
  },
  async updateEvent(id: string, request: EventRequest) {
    return (await apiClient.put(`/events/${id}`, request, decodeOrganizerEvent))
      .data;
  },
  async cancelEvent(id: string) {
    return (await apiClient.delete(`/events/${id}`, emptyResponseDecoder)).data;
  },
  async listVenues(parameters: PaginationParameters) {
    return (
      await apiClient.get('/venues', paginatedResponseDecoder(decodeVenue), {
        query: query(parameters),
      })
    ).data;
  },
  async inventory(id: string) {
    return (
      await apiClient.get(`/organizer/events/${id}/inventory`, decodeInventory)
    ).data;
  },
  async configureInventory(
    id: string,
    request: EventSeatConfigurationRequest[],
  ) {
    await apiClient.put(
      `/events/${id}/inventory`,
      request,
      unknownResponseDecoder,
    );
  },
  async bookings(id: string, parameters: PaginationParameters) {
    return (
      await apiClient.get(
        `/organizer/events/${id}/bookings`,
        paginatedResponseDecoder(decodeBooking),
        { query: query(parameters) },
      )
    ).data;
  },
};
