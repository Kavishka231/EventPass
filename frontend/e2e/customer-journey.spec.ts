import { expect, test, type Page, type Route } from '@playwright/test';

const ids = {
  event: '20000000-0000-0000-0000-000000000001',
  venue: '30000000-0000-0000-0000-000000000001',
  seat: '40000000-0000-0000-0000-000000000001',
  eventSeat: '50000000-0000-0000-0000-000000000001',
  booking: '60000000-0000-0000-0000-000000000001',
  ticket: '70000000-0000-0000-0000-000000000001',
};

const event = {
  id: ids.event,
  name: 'Colombo Jazz Evening',
  description: 'A live contemporary jazz performance.',
  category: 'Music',
  startDateTime: '2030-06-20T13:30:00Z',
  endDateTime: '2030-06-20T16:30:00Z',
  venueId: ids.venue,
  venueName: 'Lotus Hall',
  city: 'Colombo',
  status: 'PUBLISHED',
};
const seat = {
  id: ids.eventSeat,
  section: 'A',
  row: '1',
  number: '12',
  type: 'VIP',
  price: 7000,
  availability: 'AVAILABLE',
};
const sort = { empty: false, sorted: true, unsorted: false };
const pageResponse = <T>(content: T[]) => ({
  content,
  pageable: {
    pageNumber: 0,
    pageSize: 20,
    sort,
    offset: 0,
    paged: true,
    unpaged: false,
  },
  totalPages: content.length ? 1 : 0,
  totalElements: content.length,
  last: true,
  size: 20,
  number: 0,
  sort,
  numberOfElements: content.length,
  first: true,
  empty: content.length === 0,
});
const booking = {
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
    name: event.name,
    startDateTime: event.startDateTime,
    endDateTime: event.endDateTime,
  },
  venue: {
    id: ids.venue,
    name: event.venueName,
    address: '1 Lotus Road',
    city: event.city,
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
};

function auth() {
  const encode = (value: object) =>
    Buffer.from(JSON.stringify(value)).toString('base64url');
  return {
    accessToken: `${encode({ alg: 'none' })}.${encode({ sub: '10000000-0000-0000-0000-000000000001', role: 'CUSTOMER' })}.signature`,
    tokenType: 'Bearer',
    role: 'CUSTOMER',
  };
}

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function mockApi(
  page: Page,
  ticketStatus: 'ACTIVE' | 'USED' | 'CANCELLED' = 'ACTIVE',
) {
  let refreshSessionActive = false;
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/v1/auth/register' || path === '/api/v1/auth/login') {
      refreshSessionActive = true;
      return route.fulfill({
        status: path.endsWith('/register') ? 201 : 200,
        contentType: 'application/json',
        body: JSON.stringify(auth()),
        headers: {
          'Set-Cookie':
            'eventpass_refresh=e2e-session; Path=/api/v1/auth; HttpOnly; SameSite=Lax',
        },
      });
    }
    if (path === '/api/v1/auth/refresh') {
      if (!refreshSessionActive || !route.request().headers().cookie)
        return route.fulfill({ status: 401 });
      return json(route, auth());
    }
    if (path === '/api/v1/auth/csrf')
      return route.fulfill({
        status: 204,
        headers: { 'Set-Cookie': 'XSRF-TOKEN=e2e-csrf; Path=/; SameSite=Lax' },
      });
    if (path === '/api/v1/auth/logout') {
      refreshSessionActive = false;
      return route.fulfill({
        status: 204,
        headers: {
          'Set-Cookie':
            'eventpass_refresh=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax',
        },
      });
    }
    if (path === '/api/v1/events') return json(route, pageResponse([event]));
    if (path === `/api/v1/events/${ids.event}`) return json(route, event);
    if (path === `/api/v1/events/${ids.event}/seats`)
      return json(route, [seat]);
    if (path === '/api/v1/bookings' && route.request().method() === 'POST')
      return json(
        route,
        {
          id: ids.booking,
          reference: booking.reference,
          eventId: ids.event,
          status: 'CONFIRMED',
          totalAmount: 7000,
          currency: 'LKR',
          eventSeatIds: [ids.eventSeat],
          createdAt: booking.createdAt,
        },
        201,
      );
    if (path === '/api/v1/bookings')
      return json(
        route,
        pageResponse([
          {
            id: booking.id,
            reference: booking.reference,
            status: booking.status,
            totalAmount: booking.totalAmount,
            currency: booking.currency,
            seatCount: 1,
            createdAt: booking.createdAt,
            event: booking.event,
            venue: booking.venue,
          },
        ]),
      );
    if (path === `/api/v1/bookings/${ids.booking}`) return json(route, booking);
    if (path === '/api/v1/tickets')
      return json(
        route,
        pageResponse([
          {
            id: ids.ticket,
            ticketNumber: 'TKT-0001',
            bookingId: ids.booking,
            eventSeatId: ids.eventSeat,
            qrToken: ticketStatus === 'ACTIVE' ? 'active-qr-secret' : null,
            status: ticketStatus,
            issuedAt: booking.createdAt,
            usedAt: ticketStatus === 'USED' ? booking.updatedAt : null,
            bookingReference: booking.reference,
            event: booking.event,
            venue: booking.venue,
            seat: {
              id: ids.seat,
              section: 'A',
              row: '1',
              number: '12',
              type: 'VIP',
            },
          },
        ]),
      );
    if (path === '/api/v1/notifications/unread-count')
      return json(route, { unreadCount: 0 });
    return route.fulfill({ status: 404 });
  });
}

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill('customer@example.com');
  await page.getByLabel('Password', { exact: true }).fill('StrongPassword1!');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Bookings' })).toBeVisible();
}

test('restores the browser session after reload and keeps logout revoked', async ({
  page,
}) => {
  await mockApi(page);
  await login(page);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Bookings' })).toBeVisible();
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await page.goto('/bookings');
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Welcome back' }),
  ).toBeVisible();
});

test('registers and completes the mocked register-to-ticket customer journey', async ({
  page,
}) => {
  await mockApi(page);
  await page.goto('/register');
  await page.getByLabel('First name').fill('Kavi');
  await page.getByLabel('Last name').fill('Perera');
  await page.getByLabel('Email').fill('kavi@example.com');
  await page.getByLabel('Password', { exact: true }).fill('StrongPassword1!');
  await page.getByLabel('Confirm password').fill('StrongPassword1!');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('EventPass home').click();
  await page
    .getByRole('link', { name: /browse events/i })
    .first()
    .click();
  await page.getByRole('link', { name: /view event/i }).click();
  await page.getByRole('link', { name: /select seats/i }).click();
  await page.getByRole('button', { name: /seat a.*12.*available/i }).click();
  await page.getByRole('button', { name: 'Continue to checkout' }).click();
  await page.getByLabel('Sandbox payment token').fill('tok_success');
  await page.getByRole('button', { name: 'Complete booking' }).click();
  await expect(page.getByText('Your booking is confirmed')).toBeVisible();
  await page.getByRole('link', { name: 'View tickets' }).click();
  await expect(page.getByText('Scan for admission')).toBeVisible();
});

for (const status of ['USED', 'CANCELLED'] as const) {
  test(`${status.toLowerCase()} ticket never exposes QR material`, async ({
    page,
  }) => {
    await mockApi(page, status);
    await login(page);
    await page.getByRole('link', { name: 'Tickets' }).first().click();
    await expect(
      page.getByText(
        status === 'USED' ? 'Admission already completed' : 'Ticket invalid',
      ),
    ).toBeVisible();
    await expect(page.locator('body')).not.toContainText('active-qr-secret');
    expect(page.url()).not.toContain('active-qr-secret');
    await expect(page.locator('a[href*="active-qr-secret"]')).toHaveCount(0);
  });
}
