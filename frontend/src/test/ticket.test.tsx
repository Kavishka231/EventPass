import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { TicketsPage } from '../pages/TicketsPage';
import { page, ticketFixture } from './fixtures';
import { createTestQueryClient } from './render';
import { server } from './server';

function renderTickets() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>
        <TicketsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('secure digital tickets', () => {
  it('shows an active QR with complete booking context', async () => {
    renderTickets();
    expect(await screen.findByText(ticketFixture.event.name)).toBeVisible();
    expect(screen.getByText(ticketFixture.bookingReference)).toBeVisible();
    expect(screen.getByText(/row 1.*seat 12/i)).toBeVisible();
    expect(screen.getByText('Scan for admission')).toBeVisible();
  });

  for (const status of ['USED', 'CANCELLED'] as const) {
    it(`never renders the QR secret for a ${status.toLowerCase()} ticket`, async () => {
      const secret = `forbidden-${status.toLowerCase()}-secret`;
      server.use(
        http.get('*/api/v1/tickets', () =>
          HttpResponse.json(
            page([
              {
                ...ticketFixture,
                status,
                qrToken: null,
                usedAt: status === 'USED' ? new Date().toISOString() : null,
              },
            ]),
          ),
        ),
      );
      const { container } = renderTickets();
      expect(
        await screen.findByText(
          status === 'USED' ? 'Admission already completed' : 'Ticket invalid',
        ),
      ).toBeVisible();
      expect(container.innerHTML).not.toContain(secret);
      expect(document.querySelector(`[href*="${secret}"]`)).toBeNull();
      expect(screen.queryByText('Scan for admission')).not.toBeInTheDocument();
    });
  }

  it('renders an empty state', async () => {
    server.use(http.get('*/api/v1/tickets', () => HttpResponse.json(page([]))));
    renderTickets();
    expect(await screen.findByText(/no digital tickets yet/i)).toBeVisible();
  });

  it('offers retry after an API failure', async () => {
    server.use(
      http.get(
        '*/api/v1/tickets',
        () => new HttpResponse(null, { status: 503 }),
      ),
    );
    renderTickets();
    expect(
      await screen.findByRole('button', { name: /try again/i }),
    ).toBeVisible();
  });
});
