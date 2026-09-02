import { QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { LoginPage, RegistrationPage } from '../pages/AuthenticationPage';
import { RouteGuard } from '../routes/RouteGuard';
import { SessionProvider } from '../features/session';
import { credentialVault } from '../features/session/credentialVault';
import { apiClient, objectResponseDecoder } from '../services/api';
import { authFixture } from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

function renderAuth(element: React.ReactNode, route = '/login') {
  const queryClient = createTestQueryClient();
  return import('@testing-library/react').then(({ render }) =>
    render(
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <MemoryRouter initialEntries={[route]}>{element}</MemoryRouter>
        </SessionProvider>
      </QueryClientProvider>,
    ),
  );
}

beforeEach(() => credentialVault.clear());

describe('authentication flows', () => {
  it('logs in with the backend response and preserves a safe internal return route', async () => {
    await renderAuth(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/bookings" element={<h1>Bookings</h1>} />
      </Routes>,
      '/login?returnTo=%2Fbookings',
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email'), 'Customer@Example.com');
    await user.type(screen.getByLabelText('Password'), 'StrongPassword1!');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(
      await screen.findByRole('heading', { name: 'Bookings' }),
    ).toBeVisible();
    expect(credentialVault.read()?.refreshToken).toBe('rotating-refresh-token');
  });

  it('shows normalized invalid-credential feedback and keeps credentials out of the vault', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 401,
            code: 'INVALID_CREDENTIALS',
            message: 'Invalid credentials.',
            path: '/api/v1/auth/login',
            requestId: 'request-1',
          },
          { status: 401 },
        ),
      ),
    );
    await renderAuth(<LoginPage />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email'), 'customer@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/invalid email or password/i)).toBeVisible();
    expect(credentialVault.read()).toBeNull();
  });

  it('registers a customer after matching-password validation', async () => {
    await renderAuth(
      <Routes>
        <Route path="/register" element={<RegistrationPage />} />
        <Route path="/bookings" element={<h1>Bookings</h1>} />
      </Routes>,
      '/register',
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('First name'), 'Kavi');
    await user.type(screen.getByLabelText('Last name'), 'Perera');
    await user.type(screen.getByLabelText('Email'), 'kavi@example.com');
    await user.type(screen.getByLabelText('Password'), 'StrongPassword1!');
    await user.type(
      screen.getByLabelText('Confirm password'),
      'StrongPassword1!',
    );
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(
      await screen.findByRole('heading', { name: 'Bookings' }),
    ).toBeVisible();
  });

  it('redirects unauthenticated protected routes to login', async () => {
    await renderAuth(
      <Routes>
        <Route
          path="/private"
          element={
            <RouteGuard roles={['CUSTOMER']}>
              <h1>Private</h1>
            </RouteGuard>
          }
        />
        <Route path="/login" element={<h1>Login required</h1>} />
      </Routes>,
      '/private',
    );
    expect(
      await screen.findByRole('heading', { name: 'Login required' }),
    ).toBeVisible();
  });

  it('restores an in-memory session once on application start', async () => {
    credentialVault.replace(authFixture());
    let refreshCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', async () => {
        refreshCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 20));
        return HttpResponse.json(authFixture());
      }),
    );
    await renderAuth(<div>Application</div>);
    await waitFor(() => expect(refreshCalls).toBe(1));
    expect(credentialVault.read()?.accessToken).toBe(authFixture().accessToken);
  });

  it('coalesces concurrent expired-token recovery into one rotating refresh', async () => {
    credentialVault.replace(authFixture());
    let refreshCalls = 0;
    let protectedCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', async () => {
        refreshCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 20));
        return HttpResponse.json(authFixture());
      }),
      http.get('*/api/v1/protected', () => {
        protectedCalls += 1;
        return protectedCalls <= 2
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ value: 'recovered' });
      }),
    );
    function RecoveryHarness() {
      return (
        <button
          onClick={() =>
            void Promise.all([
              apiClient.get('/protected', objectResponseDecoder),
              apiClient.get('/protected', objectResponseDecoder),
            ])
          }
        >
          Load protected data
        </button>
      );
    }
    await renderAuth(<RecoveryHarness />);
    await waitFor(() => expect(refreshCalls).toBe(1));
    await userEvent.click(
      screen.getByRole('button', { name: 'Load protected data' }),
    );
    await waitFor(() => expect(protectedCalls).toBe(4));
    expect(refreshCalls).toBe(2);
  });
});
