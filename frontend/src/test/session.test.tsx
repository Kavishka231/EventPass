import { QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';

import { SessionProvider, useSession } from '../features/session';
import { credentialVault } from '../features/session/credentialVault';
import { authFixture } from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

function Harness() {
  const session = useSession();
  const queryClient = useQueryClient();
  return (
    <>
      <span>{session.status}</span>
      <button
        onClick={() => {
          queryClient.setQueryData(['private'], 'secret');
          void session.logout();
        }}
      >
        Log out
      </button>
    </>
  );
}

beforeEach(() => credentialVault.clear());

describe('session lifecycle', () => {
  it('logs out through the backend and clears user query data', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json(authFixture()),
      ),
    );
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <MemoryRouter>
            <Harness />
          </MemoryRouter>
        </SessionProvider>
      </QueryClientProvider>,
    );
    await screen.findByText('authenticated');
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
    await waitFor(() =>
      expect(screen.getByText('unauthenticated')).toBeVisible(),
    );
    expect(credentialVault.read()).toBeNull();
    expect(queryClient.getQueryData(['private'])).toBeUndefined();
  });

  it('never persists credentials in browser storage', () => {
    credentialVault.replace(authFixture());
    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
  });
});
