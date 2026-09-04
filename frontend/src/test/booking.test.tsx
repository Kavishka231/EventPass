import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { BookingDetailsPage } from '../pages/BookingDetailsPage';
import { BookingHistoryPage } from '../pages/BookingHistoryPage';
import { CheckoutPage } from '../pages/CheckoutPage';
import { EventDiscoveryPage } from '../pages/EventDiscoveryPage';
import { SeatSelectionPage } from '../pages/SeatSelectionPage';
import { bookingFixture, eventFixture, page, seatFixtures } from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

function renderRoute(path: string, pattern: string, element: React.ReactNode) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={pattern} element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderCheckout() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/checkout',
            state: {
              eventId: eventFixture.id,
              eventSeatIds: [seatFixtures[0].id],
            },
          },
        ]}
      >
        <Routes>
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route
            path="/bookings/:id/confirmation"
            element={<h1>Booking confirmed</h1>}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('event discovery and booking journey', () => {
  it('renders discovery results from the paginated backend response', async () => {
    renderRoute('/events', '/events', <EventDiscoveryPage />);
    expect(await screen.findByText(eventFixture.name)).toBeVisible();
    expect(
      screen.getAllByText(new RegExp(eventFixture.city)).length,
    ).toBeGreaterThan(0);
  });

  it('renders an empty discovery state', async () => {
    server.use(http.get('*/api/v1/events', () => HttpResponse.json(page([]))));
    renderRoute('/events', '/events', <EventDiscoveryPage />);
    expect(await screen.findByText(/no events match/i)).toBeVisible();
  });

  it('selects and deselects only available seats and updates the total', async () => {
    renderRoute(
      `/events/${eventFixture.id}/seats`,
      '/events/:eventId/seats',
      <SeatSelectionPage />,
    );
    const available = await screen.findByRole('button', {
      name: /seat a.*12.*available.*7,000/i,
    });
    const sold = screen.getByRole('button', { name: /seat a.*13.*sold/i });
    expect(sold).toBeDisabled();
    await userEvent.click(available);
    expect(screen.getByText(/1 seat selected/i)).toBeVisible();
    expect(screen.getAllByText(/7,000/).length).toBeGreaterThan(0);
    await userEvent.click(available);
    expect(screen.getAllByText(/select seats/i).length).toBeGreaterThan(0);
  });

  it('submits checkout once with one stable idempotency key and reaches confirmation', async () => {
    let requests = 0;
    let key = '';
    server.use(
      http.post('*/api/v1/bookings', async ({ request }) => {
        requests += 1;
        key = request.headers.get('Idempotency-Key') ?? '';
        await new Promise((resolve) => setTimeout(resolve, 20));
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
    );
    renderCheckout();
    await userEvent.type(
      await screen.findByLabelText('Sandbox payment token'),
      'tok_success',
    );
    const submit = screen.getByRole('button', { name: 'Complete booking' });
    await Promise.all([userEvent.click(submit), userEvent.click(submit)]);
    expect(
      await screen.findByRole('heading', { name: 'Booking confirmed' }),
    ).toBeVisible();
    expect(requests).toBe(1);
    expect(key).not.toBe('');
  });

  it.each([
    ['PAYMENT_FAILED', /payment could not be completed/i],
    ['PAYMENT_OUTCOME_UNKNOWN', /confirming your payment/i],
  ])('presents %s without claiming confirmation', async (code, message) => {
    server.use(
      http.post('*/api/v1/bookings', () =>
        HttpResponse.json(
          {
            status: code === 'PAYMENT_FAILED' ? 422 : 503,
            code,
            message: 'Provider outcome',
            path: '/api/v1/bookings',
            requestId: 'req-payment',
            timestamp: new Date().toISOString(),
          },
          { status: code === 'PAYMENT_FAILED' ? 422 : 503 },
        ),
      ),
    );
    renderCheckout();
    await userEvent.type(
      await screen.findByLabelText('Sandbox payment token'),
      code === 'PAYMENT_FAILED' ? 'tok_fail' : 'tok_unknown',
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Complete booking' }),
    );
    expect(await screen.findByText(message)).toBeVisible();
    expect(
      screen.queryByRole('heading', { name: 'Booking confirmed' }),
    ).not.toBeInTheDocument();
  });

  it('renders enriched booking history without additional public event requests', async () => {
    let publicEventRequests = 0;
    server.use(
      http.get('*/api/v1/events/:id', () => {
        publicEventRequests += 1;
        return HttpResponse.json(eventFixture);
      }),
    );
    renderRoute('/bookings', '/bookings', <BookingHistoryPage />);
    expect(
      (await screen.findAllByText(bookingFixture.reference))[0],
    ).toBeVisible();
    expect(screen.getByText(eventFixture.name)).toBeVisible();
    expect(publicEventRequests).toBe(0);
  });

  it('renders enriched booking, payment, seat and venue details', async () => {
    renderRoute(
      `/bookings/${bookingFixture.id}`,
      '/bookings/:bookingId',
      <BookingDetailsPage />,
    );
    expect(
      (await screen.findAllByText(bookingFixture.reference))[0],
    ).toBeVisible();
    expect(screen.getByText(eventFixture.name)).toBeVisible();
    expect(screen.getByText(/row 1.*seat 12/i)).toBeVisible();
    expect(screen.getAllByText('SUCCESS').length).toBeGreaterThan(0);
  });

  it('prevents duplicate cancellation submissions while the first is pending', async () => {
    let cancellations = 0;
    server.use(
      http.post(`*/api/v1/bookings/${bookingFixture.id}/cancel`, async () => {
        cancellations += 1;
        await new Promise((resolve) => setTimeout(resolve, 30));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoute(
      `/bookings/${bookingFixture.id}`,
      '/bookings/:bookingId',
      <BookingDetailsPage />,
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'Cancel booking' }),
    );
    const confirm = screen.getByRole('button', {
      name: 'Confirm cancellation',
    });
    await Promise.all([userEvent.click(confirm), userEvent.click(confirm)]);
    await waitFor(() => expect(cancellations).toBe(1));
  });

  it('shows a normalized cancellation failure without changing local state', async () => {
    server.use(
      http.post(`*/api/v1/bookings/${bookingFixture.id}/cancel`, () =>
        HttpResponse.json(
          {
            status: 409,
            code: 'BOOKING_NOT_CANCELLABLE',
            message: 'Not cancellable',
            path: `/api/v1/bookings/${bookingFixture.id}/cancel`,
            requestId: 'req-1',
            timestamp: new Date().toISOString(),
          },
          { status: 409 },
        ),
      ),
    );
    renderRoute(
      `/bookings/${bookingFixture.id}`,
      '/bookings/:bookingId',
      <BookingDetailsPage />,
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'Cancel booking' }),
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Confirm cancellation' }),
    );
    expect(await screen.findByText(/no longer eligible/i)).toBeVisible();
  });
});
