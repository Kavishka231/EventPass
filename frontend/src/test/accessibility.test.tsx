import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import axe from 'axe-core';
import { describe, expect, it } from 'vitest';

import { LoginPage } from '../pages/AuthenticationPage';
import { BookingDetailsPage } from '../pages/BookingDetailsPage';
import { CheckoutPage } from '../pages/CheckoutPage';
import { EventDiscoveryPage } from '../pages/EventDiscoveryPage';
import { NotificationsPage } from '../pages/NotificationsPage';
import { SeatSelectionPage } from '../pages/SeatSelectionPage';
import { TicketsPage } from '../pages/TicketsPage';
import { SessionProvider } from '../features/session';
import { createTestQueryClient } from './render';
import { eventFixture, bookingFixture, seatFixtures } from './fixtures';

function renderRoute(
  path: string,
  pattern: string,
  element: React.ReactNode,
  session = false,
) {
  const content = (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={pattern} element={element} />
      </Routes>
    </MemoryRouter>
  );
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      {session ? <SessionProvider>{content}</SessionProvider> : content}
    </QueryClientProvider>,
  );
}

describe('critical screen accessibility', () => {
  it('has no detectable login violations', async () => {
    const view = renderRoute('/login', '/login', <LoginPage />, true);
    expect((await axe.run(view.container)).violations).toEqual([]);
  });

  it('has no detectable discovery violations', async () => {
    const view = renderRoute('/events', '/events', <EventDiscoveryPage />);
    await view.findByText(eventFixture.name);
    expect((await axe.run(view.container)).violations).toEqual([]);
  });

  it('has no detectable seat-selection violations', async () => {
    const view = renderRoute(
      `/events/${eventFixture.id}/seats`,
      '/events/:eventId/seats',
      <SeatSelectionPage />,
    );
    await view.findByRole('button', { name: /seat a.*12.*available/i });
    expect((await axe.run(view.container)).violations).toEqual([]);
  });

  it('has no detectable booking-detail violations', async () => {
    const view = renderRoute(
      `/bookings/${bookingFixture.id}`,
      '/bookings/:bookingId',
      <BookingDetailsPage />,
    );
    await view.findByRole('heading', { name: bookingFixture.reference });
    expect((await axe.run(view.container)).violations).toEqual([]);
  });

  it('has no detectable checkout violations', async () => {
    const view = render(
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
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await view.findByLabelText('Sandbox payment token');
    expect((await axe.run(view.container)).violations).toEqual([]);
  });

  it('has no detectable ticket violations', async () => {
    const view = renderRoute('/tickets', '/tickets', <TicketsPage />);
    await view.findByText('Scan for admission');
    expect((await axe.run(view.container)).violations).toEqual([]);
  });

  it('has no detectable notification violations', async () => {
    const view = renderRoute(
      '/notifications',
      '/notifications',
      <NotificationsPage />,
    );
    await view.findByText('Booking confirmed');
    expect((await axe.run(view.container)).violations).toEqual([]);
  });
});
