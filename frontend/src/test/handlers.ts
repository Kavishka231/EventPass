import { delay, http, HttpResponse } from 'msw';

import {
  authFixture,
  bookingFixture,
  eventFixture,
  notificationFixture,
  page,
  seatFixtures,
  ticketFixture,
} from './fixtures';

export const handlers = [
  http.post('*/api/v1/auth/login', () => HttpResponse.json(authFixture())),
  http.post('*/api/v1/auth/register', () => HttpResponse.json(authFixture())),
  http.post('*/api/v1/auth/refresh', () => HttpResponse.json(authFixture())),
  http.post(
    '*/api/v1/auth/logout',
    () => new HttpResponse(null, { status: 204 }),
  ),
  http.get('*/api/v1/events', () => HttpResponse.json(page([eventFixture]))),
  http.get(`*/api/v1/events/${eventFixture.id}`, () =>
    HttpResponse.json(eventFixture),
  ),
  http.get(`*/api/v1/events/${eventFixture.id}/seats`, () =>
    HttpResponse.json(seatFixtures),
  ),
  http.post('*/api/v1/bookings', async () => {
    await delay(20);
    return HttpResponse.json(
      {
        id: bookingFixture.id,
        reference: bookingFixture.reference,
        eventId: eventFixture.id,
        status: 'CONFIRMED',
        totalAmount: 7000,
        currency: 'LKR',
        eventSeatIds: [seatFixtures[0].id],
        createdAt: bookingFixture.createdAt,
      },
      { status: 201 },
    );
  }),
  http.get('*/api/v1/bookings', () =>
    HttpResponse.json(
      page([
        {
          id: bookingFixture.id,
          reference: bookingFixture.reference,
          status: bookingFixture.status,
          totalAmount: bookingFixture.totalAmount,
          currency: bookingFixture.currency,
          seatCount: 1,
          createdAt: bookingFixture.createdAt,
          event: bookingFixture.event,
          venue: bookingFixture.venue,
        },
      ]),
    ),
  ),
  http.get(`*/api/v1/bookings/${bookingFixture.id}`, () =>
    HttpResponse.json(bookingFixture),
  ),
  http.post(
    `*/api/v1/bookings/${bookingFixture.id}/cancel`,
    () => new HttpResponse(null, { status: 204 }),
  ),
  http.get('*/api/v1/tickets', () => HttpResponse.json(page([ticketFixture]))),
  http.get('*/api/v1/notifications', () =>
    HttpResponse.json(page([notificationFixture])),
  ),
  http.get('*/api/v1/notifications/unread-count', () =>
    HttpResponse.json({ unreadCount: 1 }),
  ),
  http.patch(
    `*/api/v1/notifications/${notificationFixture.id}/read`,
    () => new HttpResponse(null, { status: 204 }),
  ),
];
