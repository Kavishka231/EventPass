export const ids = {
  user: '10000000-0000-0000-0000-000000000001',
  event: '20000000-0000-0000-0000-000000000001',
  venue: '30000000-0000-0000-0000-000000000001',
  seat: '40000000-0000-0000-0000-000000000001',
  eventSeat: '50000000-0000-0000-0000-000000000001',
  booking: '60000000-0000-0000-0000-000000000001',
  ticket: '70000000-0000-0000-0000-000000000001',
  notification: '80000000-0000-0000-0000-000000000001',
} as const;

export const eventFixture = {
  id: ids.event,
  name: 'Colombo Jazz Evening',
  description: 'An evening of contemporary jazz.',
  category: 'Music',
  startDateTime: '2030-06-20T13:30:00Z',
  endDateTime: '2030-06-20T16:30:00Z',
  venueId: ids.venue,
  venueName: 'Lotus Hall',
  city: 'Colombo',
  status: 'PUBLISHED',
} as const;

export const seatFixtures = [
  {
    id: ids.eventSeat,
    section: 'A',
    row: '1',
    number: '12',
    type: 'VIP',
    price: 7000,
    availability: 'AVAILABLE',
  },
  {
    id: '50000000-0000-0000-0000-000000000002',
    section: 'A',
    row: '1',
    number: '13',
    type: 'VIP',
    price: 7000,
    availability: 'SOLD',
  },
] as const;

export const bookingFixture = {
  id: ids.booking,
  reference: 'EVP-2030-0001',
  status: 'CONFIRMED',
  totalAmount: 7000,
  currency: 'LKR',
  createdAt: '2030-01-01T10:00:00Z',
  updatedAt: '2030-01-01T10:01:00Z',
  expiresAt: '2030-01-01T10:15:00Z',
  event: {
    id: ids.event,
    name: eventFixture.name,
    startDateTime: eventFixture.startDateTime,
    endDateTime: eventFixture.endDateTime,
  },
  venue: {
    id: ids.venue,
    name: eventFixture.venueName,
    address: '1 Lotus Road',
    city: eventFixture.city,
  },
  seats: [
    {
      eventSeatId: ids.eventSeat,
      seatId: ids.seat,
      section: 'A',
      row: '1',
      number: '12',
      type: 'VIP',
      unitPrice: 7000,
    },
  ],
  payment: {
    status: 'SUCCESS',
    attemptedAt: '2030-01-01T10:00:30Z',
    completedAt: '2030-01-01T10:01:00Z',
  },
  refund: null,
} as const;

export const ticketFixture = {
  id: ids.ticket,
  ticketNumber: 'TKT-0001',
  bookingId: ids.booking,
  eventSeatId: ids.eventSeat,
  qrToken: 'secure-active-ticket-token',
  status: 'ACTIVE',
  issuedAt: '2030-01-01T10:01:00Z',
  usedAt: null,
  bookingReference: bookingFixture.reference,
  event: bookingFixture.event,
  venue: bookingFixture.venue,
  seat: { id: ids.seat, section: 'A', row: '1', number: '12', type: 'VIP' },
} as const;

export const notificationFixture = {
  id: ids.notification,
  type: 'BOOKING_CREATED',
  title: 'Booking confirmed',
  message: 'Your booking is confirmed.',
  createdAt: '2030-01-01T10:01:00Z',
  readAt: null,
} as const;

export function page<T>(content: T[]) {
  const sort = { empty: false, sorted: true, unsorted: false };
  return {
    content,
    pageable: {
      pageNumber: 0,
      pageSize: 20,
      sort,
      offset: 0,
      paged: true,
      unpaged: false,
    },
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    numberOfElements: content.length,
    sort,
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

function base64Url(value: object) {
  return btoa(JSON.stringify(value))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '');
}

export function authFixture(
  role: 'CUSTOMER' | 'ORGANIZER' | 'ADMIN' = 'CUSTOMER',
) {
  return {
    accessToken: `${base64Url({ alg: 'none' })}.${base64Url({ sub: ids.user, role })}.signature`,
    tokenType: 'Bearer',
    role,
  };
}
