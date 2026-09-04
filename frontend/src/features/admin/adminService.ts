import {
  apiClient,
  emptyResponseDecoder,
  objectResponseDecoder,
  paginatedResponseDecoder,
} from '../../services/api';
import type {
  AdminBookingResponse,
  AdminBookingSearchParameters,
  AdminStatisticsResponse,
  AdminUserResponse,
  CreateSeatRequest,
  EventRequest,
  PaginatedResponse,
  PaginationParameters,
  SeatDefinitionResponse,
  UpdateUserRequest,
  VenueRequest,
  VenueResponse,
} from '../../types';
import { decodeOrganizerEvent } from '../organizer';

const text = (o: Record<string, unknown>, f: string) => {
  const v = o[f];
  if (typeof v !== 'string' || !v) throw new Error(`Expected ${f}.`);
  return v;
};
const number = (o: Record<string, unknown>, f: string) => {
  const v = o[f];
  if (typeof v !== 'number' || !Number.isFinite(v))
    throw new Error(`Expected ${f}.`);
  return v;
};
const userDecoder = (value: unknown): AdminUserResponse => {
  const o = objectResponseDecoder(value);
  return {
    id: text(o, 'id'),
    email: text(o, 'email'),
    firstName: text(o, 'firstName'),
    lastName: text(o, 'lastName'),
    role: text(o, 'role') as AdminUserResponse['role'],
    status: text(o, 'status') as AdminUserResponse['status'],
    createdAt: text(o, 'createdAt'),
  };
};
const venueDecoder = (value: unknown): VenueResponse => {
  const o = objectResponseDecoder(value);
  return {
    id: text(o, 'id'),
    name: text(o, 'name'),
    address: text(o, 'address'),
    city: text(o, 'city'),
    capacity: number(o, 'capacity'),
  };
};
const seatDecoder = (value: unknown): SeatDefinitionResponse => {
  const o = objectResponseDecoder(value);
  return {
    id: text(o, 'id'),
    venueId: text(o, 'venueId'),
    section: text(o, 'section'),
    row: text(o, 'row'),
    number: text(o, 'number'),
    type: text(o, 'type') as SeatDefinitionResponse['type'],
  };
};
const bookingDecoder = (value: unknown): AdminBookingResponse => {
  const o = objectResponseDecoder(value);
  if (!Array.isArray(o.eventSeatIds)) throw new Error('Expected eventSeatIds.');
  return {
    id: text(o, 'id'),
    reference: text(o, 'reference'),
    eventId: text(o, 'eventId'),
    eventName: text(o, 'eventName'),
    customerId: text(o, 'customerId'),
    customerEmail: text(o, 'customerEmail'),
    status: text(o, 'status') as AdminBookingResponse['status'],
    totalAmount: number(o, 'totalAmount'),
    currency: text(o, 'currency'),
    eventSeatIds: o.eventSeatIds.map(String),
    createdAt: text(o, 'createdAt'),
  };
};
const statisticsDecoder = (value: unknown): AdminStatisticsResponse => {
  const o = objectResponseDecoder(value);
  return {
    users: number(o, 'users'),
    events: number(o, 'events'),
    venues: number(o, 'venues'),
    bookings: number(o, 'bookings'),
    confirmedBookings: number(o, 'confirmedBookings'),
  };
};
const params = (p: PaginationParameters) => ({
  page: p.page,
  size: p.size,
  sort: p.sort,
});
const seatListDecoder = (value: unknown) => {
  if (!Array.isArray(value)) throw new Error('Expected seats.');
  return value.map(seatDecoder);
};

export const adminService = {
  async statistics() {
    return (await apiClient.get('/admin/statistics', statisticsDecoder)).data;
  },
  async users(p: PaginationParameters) {
    return (
      await apiClient.get(
        '/admin/users',
        paginatedResponseDecoder(userDecoder),
        { query: params(p) },
      )
    ).data;
  },
  async updateUser(id: string, request: UpdateUserRequest) {
    return (await apiClient.put(`/admin/users/${id}`, request, userDecoder))
      .data;
  },
  async venues(p: PaginationParameters) {
    return (
      await apiClient.get('/venues', paginatedResponseDecoder(venueDecoder), {
        query: params(p),
      })
    ).data;
  },
  async venue(id: string) {
    return (await apiClient.get(`/venues/${id}`, venueDecoder)).data;
  },
  async createVenue(request: VenueRequest) {
    return (await apiClient.post('/venues', request, venueDecoder)).data;
  },
  async updateVenue(id: string, request: VenueRequest) {
    return (await apiClient.put(`/venues/${id}`, request, venueDecoder)).data;
  },
  async deleteVenue(id: string) {
    await apiClient.delete(`/venues/${id}`, emptyResponseDecoder);
  },
  async seats(
    id: string,
    p: PaginationParameters,
  ): Promise<PaginatedResponse<SeatDefinitionResponse>> {
    return (
      await apiClient.get(
        `/admin/venues/${id}/seats`,
        paginatedResponseDecoder(seatDecoder),
        { query: params(p) },
      )
    ).data;
  },
  async createSeats(id: string, request: CreateSeatRequest[]) {
    return (
      await apiClient.post(`/venues/${id}/seats`, request, seatListDecoder)
    ).data;
  },
  async events(p: PaginationParameters) {
    return (
      await apiClient.get(
        '/admin/events',
        paginatedResponseDecoder(decodeOrganizerEvent),
        { query: params(p) },
      )
    ).data;
  },
  async event(id: string) {
    return (await apiClient.get(`/admin/events/${id}`, decodeOrganizerEvent))
      .data;
  },
  async updateEvent(id: string, request: EventRequest) {
    return (await apiClient.put(`/events/${id}`, request, decodeOrganizerEvent))
      .data;
  },
  async cancelEvent(id: string) {
    await apiClient.delete(`/events/${id}`, emptyResponseDecoder);
  },
  async bookings(p: AdminBookingSearchParameters) {
    return (
      await apiClient.get(
        '/admin/bookings',
        paginatedResponseDecoder(bookingDecoder),
        {
          query: {
            eventId: p.eventId,
            status: p.status,
            page: p.page,
            size: p.size,
            sort: p.sort,
          },
        },
      )
    ).data;
  },
  async booking(id: string) {
    return (await apiClient.get(`/admin/bookings/${id}`, bookingDecoder)).data;
  },
  async cancelBooking(id: string) {
    await apiClient.post(
      `/admin/bookings/${id}/cancel`,
      undefined,
      emptyResponseDecoder,
    );
  },
};
