import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { CustomerShell } from '../layouts/CustomerShell';
import { NotificationsPage } from '../pages/NotificationsPage';
import { page } from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

function renderPage(element: React.ReactNode) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>{element}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('customer notifications', () => {
  it('distinguishes unread notifications and marks one read once', async () => {
    let calls = 0;
    server.use(
      http.patch('*/api/v1/notifications/:id/read', async () => {
        calls += 1;
        await new Promise((resolve) => setTimeout(resolve, 20));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage(<NotificationsPage />);
    expect(await screen.findByText('Unread')).toBeVisible();
    const button = screen.getByRole('button', {
      name: /mark.*booking confirmed.*as read/i,
    });
    await Promise.all([userEvent.click(button), userEvent.click(button)]);
    await waitFor(() => expect(calls).toBe(1));
  });

  it('preserves unread state when mark-as-read fails', async () => {
    server.use(
      http.patch(
        '*/api/v1/notifications/:id/read',
        () => new HttpResponse(null, { status: 503 }),
      ),
    );
    renderPage(<NotificationsPage />);
    await userEvent.click(
      await screen.findByRole('button', { name: /mark.*as read/i }),
    );
    expect(await screen.findByText(/remains unread/i)).toBeVisible();
    expect(screen.getByText('Unread')).toBeVisible();
  });

  it('caps the navigation badge at 99+', async () => {
    server.use(
      http.get('*/api/v1/notifications/unread-count', () =>
        HttpResponse.json({ unreadCount: 125 }),
      ),
    );
    renderPage(<CustomerShell />);
    expect((await screen.findAllByText('99+'))[0]).toHaveAccessibleName(
      '125 unread notifications',
    );
  });

  it('hides a zero unread badge', async () => {
    server.use(
      http.get('*/api/v1/notifications/unread-count', () =>
        HttpResponse.json({ unreadCount: 0 }),
      ),
    );
    renderPage(<CustomerShell />);
    await screen.findAllByText('Notifications');
    expect(
      screen.queryByLabelText(/unread notification/i),
    ).not.toBeInTheDocument();
  });

  it('renders empty and error states from backend pagination', async () => {
    server.use(
      http.get('*/api/v1/notifications', () => HttpResponse.json(page([]))),
    );
    const view = renderPage(<NotificationsPage />);
    expect(await screen.findByText(/no notifications yet/i)).toBeVisible();
    view.unmount();
    server.use(
      http.get(
        '*/api/v1/notifications',
        () => new HttpResponse(null, { status: 500 }),
      ),
    );
    renderPage(<NotificationsPage />);
    expect(
      await screen.findByRole('button', { name: /try again/i }),
    ).toBeVisible();
  });
});
