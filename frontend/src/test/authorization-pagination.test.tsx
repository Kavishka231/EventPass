import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { SessionProvider } from '../features/session';
import { credentialVault } from '../features/session/credentialVault';
import { EventDiscoveryPage } from '../pages/EventDiscoveryPage';
import { NotificationsPage } from '../pages/NotificationsPage';
import { TicketsPage } from '../pages/TicketsPage';
import { RouteGuard } from '../routes/RouteGuard';
import {
  authFixture,
  eventFixture,
  notificationFixture,
  page,
  ticketFixture,
} from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

function renderRoute(
  path: string,
  pattern: string,
  element: React.ReactNode,
  session = false,
) {
  const routes = (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={pattern} element={element} />
        <Route path="/unauthorized" element={<h1>Access denied</h1>} />
      </Routes>
    </MemoryRouter>
  );
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      {session ? <SessionProvider>{routes}</SessionProvider> : routes}
    </QueryClientProvider>,
  );
}

beforeEach(() => credentialVault.clear());

describe('authorization and server pagination', () => {
  it('rejects an authenticated organizer from customer-only routes', async () => {
    credentialVault.replace(authFixture('ORGANIZER'));
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json(authFixture('ORGANIZER')),
      ),
    );
    renderRoute(
      '/bookings',
      '/bookings',
      <RouteGuard roles={['CUSTOMER']}>
        <h1>Customer bookings</h1>
      </RouteGuard>,
      true,
    );
    expect(
      await screen.findByRole('heading', { name: 'Access denied' }),
    ).toBeVisible();
    expect(screen.queryByText('Customer bookings')).not.toBeInTheDocument();
  });

  it.each([
    ['events', <EventDiscoveryPage />, eventFixture],
    ['tickets', <TicketsPage />, ticketFixture],
    ['notifications', <NotificationsPage />, notificationFixture],
  ] as const)(
    'requests the next backend page for %s',
    async (resource, element, item) => {
      const requestedPages: string[] = [];
      server.use(
        http.get(`*/api/v1/${resource}`, ({ request }) => {
          requestedPages.push(
            new URL(request.url).searchParams.get('page') ?? '0',
          );
          return HttpResponse.json({
            ...page([item]),
            totalPages: 2,
            totalElements: 21,
            last: false,
          });
        }),
      );
      renderRoute(`/${resource}`, `/${resource}`, element);
      await userEvent.click(
        await screen.findByRole('button', { name: 'Next' }),
      );
      await waitFor(() => expect(requestedPages).toContain('1'));
    },
  );
});
