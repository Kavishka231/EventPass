import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SessionProvider } from '../features/session';
import { credentialVault } from '../features/session/credentialVault';
import { AdmissionPage } from '../pages/AdmissionPage';
import { RouteGuard } from '../routes/RouteGuard';
import { authFixture, eventFixture, page, ticketFixture } from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

const secret = 'manual-admission-secret';

function renderAdmission() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>
        <AdmissionPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function selectEventAndEnterToken() {
  const user = userEvent.setup();
  await user.selectOptions(
    await screen.findByLabelText('Event'),
    eventFixture.id,
  );
  await user.type(screen.getByLabelText('Ticket token'), secret);
  return user;
}

function apiFailure(code: string, status = 409) {
  return HttpResponse.json(
    {
      timestamp: new Date().toISOString(),
      status,
      code,
      message: 'Admission rejected.',
      path: '/api/v1/tickets/validate',
      requestId: 'admission-request',
    },
    { status },
  );
}

beforeEach(() => credentialVault.clear());
afterEach(() => vi.unstubAllGlobals());

describe('ticket admission', () => {
  it('requires explicit confirmation between validation and atomic redemption', async () => {
    let validations = 0;
    let redemptions = 0;
    server.use(
      http.post('*/api/v1/tickets/validate', () => {
        validations += 1;
        return HttpResponse.json({
          ticketId: ticketFixture.id,
          ticketNumber: ticketFixture.ticketNumber,
          eventId: eventFixture.id,
          eventSeatId: ticketFixture.eventSeatId,
          status: 'ACTIVE',
          eventName: eventFixture.name,
          eventStartDateTime: eventFixture.startDateTime,
        });
      }),
      http.post('*/api/v1/tickets/redeem', () => {
        redemptions += 1;
        return HttpResponse.json({
          ticketId: ticketFixture.id,
          ticketNumber: ticketFixture.ticketNumber,
          eventId: eventFixture.id,
          status: 'USED',
          usedAt: '2030-06-20T13:31:00Z',
        });
      }),
    );
    renderAdmission();
    const user = await selectEventAndEnterToken();
    await user.click(screen.getByRole('button', { name: 'Validate ticket' }));
    expect(await screen.findByText('Confirm ticket redemption')).toBeVisible();
    expect(validations).toBe(1);
    expect(redemptions).toBe(0);
    await user.click(screen.getByRole('button', { name: 'Confirm admission' }));
    expect(await screen.findByText('Admission confirmed')).toBeVisible();
    expect(redemptions).toBe(1);
    expect(document.body.textContent).not.toContain(secret);
    expect(window.location.href).not.toContain(secret);
    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
  });

  it.each([
    ['TICKET_EVENT_MISMATCH', 'different event'],
    ['TICKET_ALREADY_USED', 'already been used'],
    ['TICKET_CANCELLED', 'cancelled'],
    ['TICKET_NOT_FOUND', 'invalid or no longer exists'],
  ])('presents %s validation safely', async (code, message) => {
    server.use(http.post('*/api/v1/tickets/validate', () => apiFailure(code)));
    renderAdmission();
    const user = await selectEventAndEnterToken();
    await user.click(screen.getByRole('button', { name: 'Validate ticket' }));
    expect(await screen.findByText(new RegExp(message, 'i'))).toBeVisible();
    expect(document.body.textContent).not.toContain(secret);
  });

  it('redirects an unauthenticated visitor away from admission', async () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <SessionProvider>
          <MemoryRouter initialEntries={['/admission']}>
            <Routes>
              <Route
                path="/admission"
                element={
                  <RouteGuard roles={['ORGANIZER', 'ADMIN']}>
                    <h1>Admission allowed</h1>
                  </RouteGuard>
                }
              />
              <Route path="/login" element={<h1>Sign in required</h1>} />
            </Routes>
          </MemoryRouter>
        </SessionProvider>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByRole('heading', { name: 'Sign in required' }),
    ).toBeVisible();
  });

  it('handles validation network/API failure without exposing the token', async () => {
    server.use(
      http.post('*/api/v1/tickets/validate', () =>
        apiFailure('SERVICE_UNAVAILABLE', 503),
      ),
    );
    renderAdmission();
    const user = await selectEventAndEnterToken();
    await user.click(screen.getByRole('button', { name: 'Validate ticket' }));
    expect(await screen.findByText(/service is unavailable/i)).toBeVisible();
    expect(document.body.textContent).not.toContain(secret);
  });

  it('handles redemption races as authoritative server failures', async () => {
    server.use(
      http.post('*/api/v1/tickets/redeem', () =>
        apiFailure('TICKET_ALREADY_USED'),
      ),
    );
    renderAdmission();
    const user = await selectEventAndEnterToken();
    await user.click(screen.getByRole('button', { name: 'Validate ticket' }));
    await user.click(
      await screen.findByRole('button', { name: 'Confirm admission' }),
    );
    expect(await screen.findByText(/already been used/i)).toBeVisible();
    expect(document.body.textContent).not.toContain(secret);
  });

  it('prevents duplicate redemption submissions', async () => {
    let redemptions = 0;
    server.use(
      http.post('*/api/v1/tickets/redeem', async () => {
        redemptions += 1;
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          ticketId: ticketFixture.id,
          ticketNumber: ticketFixture.ticketNumber,
          eventId: eventFixture.id,
          status: 'USED',
          usedAt: '2030-06-20T13:31:00Z',
        });
      }),
    );
    renderAdmission();
    const user = await selectEventAndEnterToken();
    await user.click(screen.getByRole('button', { name: 'Validate ticket' }));
    const confirm = await screen.findByRole('button', {
      name: 'Confirm admission',
    });
    await Promise.all([user.click(confirm), user.click(confirm)]);
    await screen.findByText('Admission confirmed');
    expect(redemptions).toBe(1);
  });

  it('offers manual entry when camera APIs are unavailable', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: undefined,
    });
    renderAdmission();
    const user = userEvent.setup();
    await user.selectOptions(
      await screen.findByLabelText('Event'),
      eventFixture.id,
    );
    await user.click(screen.getByRole('button', { name: 'Start camera' }));
    expect(
      await screen.findByText(/not supported by this browser/i),
    ).toBeVisible();
    expect(screen.getByLabelText('Ticket token')).toBeEnabled();
  });

  it.each([
    ['ORGANIZER', true],
    ['ADMIN', true],
    ['CUSTOMER', false],
  ] as const)('enforces the admission route for %s', async (role, allowed) => {
    credentialVault.replace(authFixture(role));
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json(authFixture(role)),
      ),
    );
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <SessionProvider>
          <MemoryRouter initialEntries={['/admission']}>
            <Routes>
              <Route
                path="/admission"
                element={
                  <RouteGuard roles={['ORGANIZER', 'ADMIN']}>
                    <h1>Admission allowed</h1>
                  </RouteGuard>
                }
              />
              <Route path="/unauthorized" element={<h1>Access denied</h1>} />
            </Routes>
          </MemoryRouter>
        </SessionProvider>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByRole('heading', {
        name: allowed ? 'Admission allowed' : 'Access denied',
      }),
    ).toBeVisible();
  });

  it('requires an event before a token can be processed', async () => {
    server.use(
      http.get('*/api/v1/events', () =>
        HttpResponse.json(page([eventFixture])),
      ),
    );
    renderAdmission();
    expect(await screen.findByLabelText('Ticket token')).toBeDisabled();
  });
});
