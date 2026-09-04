import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  OrganizerBookingsPage,
  OrganizerEventFormPage,
  OrganizerEventsPage,
  OrganizerInventoryPage,
} from '../pages/OrganizerPages';
import { renderWithApp } from './render';
import { server } from './server';
import { page } from './fixtures';

const event = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Organizer Summit',
  description: 'A useful event',
  category: 'Business',
  startDateTime: '2031-06-20T10:00:00Z',
  endDateTime: '2031-06-20T12:00:00Z',
  venueId: '22222222-2222-2222-2222-222222222222',
  venueName: 'City Hall',
  city: 'Colombo',
  status: 'DRAFT',
};
const venue = {
  id: event.venueId,
  name: event.venueName,
  address: 'Main Street',
  city: event.city,
  capacity: 2,
};

beforeEach(() => {
  server.use(
    http.get('*/api/v1/organizer/events', () =>
      HttpResponse.json(page([event])),
    ),
    http.get(`*/api/v1/organizer/events/${event.id}`, () =>
      HttpResponse.json(event),
    ),
    http.get('*/api/v1/venues', () => HttpResponse.json(page([venue]))),
  );
});

describe('organizer workspace', () => {
  it('lists only the organizer event actions', async () => {
    renderWithApp(<OrganizerEventsPage />, '/organizer/events');
    expect(
      await screen.findByRole('heading', { name: event.name }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inventory' })).toHaveAttribute(
      'href',
      `/organizer/events/${event.id}/inventory`,
    );
    expect(screen.getByRole('link', { name: 'Bookings' })).toBeInTheDocument();
  });

  it('creates a draft with the selected existing venue', async () => {
    const created = vi.fn();
    server.use(
      http.post('*/api/v1/events', async ({ request }) => {
        created(await request.json());
        return HttpResponse.json(event, { status: 201 });
      }),
    );
    renderWithApp(<OrganizerEventFormPage />, '/organizer/events/new');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Event name'), event.name);
    await user.type(screen.getByLabelText('Category'), event.category);
    await user.type(screen.getByLabelText('Starts'), '2031-06-20T10:00');
    await user.type(screen.getByLabelText('Ends'), '2031-06-20T12:00');
    await user.selectOptions(screen.getByLabelText('Venue'), event.venueId);
    await user.type(screen.getByLabelText('Description'), event.description);
    await user.click(screen.getByRole('button', { name: 'Create draft' }));
    await waitFor(() =>
      expect(created).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'DRAFT', venueId: event.venueId }),
      ),
    );
  });

  it('configures authoritative pricing and blocking', async () => {
    const saved = vi.fn();
    server.use(
      http.get(`*/api/v1/organizer/events/${event.id}/inventory`, () =>
        HttpResponse.json([
          {
            seatId: 'seat-1',
            section: 'A',
            row: '1',
            number: '1',
            type: 'REGULAR',
            price: null,
            blocked: false,
            configured: false,
          },
        ]),
      ),
      http.put(`*/api/v1/events/${event.id}/inventory`, async ({ request }) => {
        saved(await request.json());
        return HttpResponse.json([]);
      }),
    );
    renderWithApp(
      <Routes>
        <Route
          path="/organizer/events/:eventId/inventory"
          element={<OrganizerInventoryPage />}
        />
      </Routes>,
      `/organizer/events/${event.id}/inventory`,
    );
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText('Configure A 1-1'));
    await user.clear(screen.getByLabelText('Price for A 1-1'));
    await user.type(screen.getByLabelText('Price for A 1-1'), '2500');
    await user.click(screen.getByLabelText('Block'));
    await user.click(screen.getByRole('button', { name: 'Save inventory' }));
    await waitFor(() =>
      expect(saved).toHaveBeenCalledWith([
        { seatId: 'seat-1', price: 2500, blocked: true },
      ]),
    );
  });

  it('renders the paginated event booking report', async () => {
    server.use(
      http.get(`*/api/v1/organizer/events/${event.id}/bookings`, () =>
        HttpResponse.json(
          page([
            {
              id: 'booking-1',
              reference: 'EVP-001',
              eventId: event.id,
              customerId: 'customer-1',
              customerEmail: 'customer@example.com',
              customerFirstName: 'Ada',
              customerLastName: 'Lovelace',
              status: 'CONFIRMED',
              totalAmount: 2500,
              currency: 'LKR',
              eventSeatIds: ['seat-1'],
              createdAt: '2030-01-01T10:00:00Z',
            },
          ]),
        ),
      ),
    );
    renderWithApp(
      <Routes>
        <Route
          path="/organizer/events/:eventId/bookings"
          element={<OrganizerBookingsPage />}
        />
      </Routes>,
      `/organizer/events/${event.id}/bookings`,
    );
    expect(await screen.findByText('EVP-001')).toBeInTheDocument();
    expect(screen.getByText('customer@example.com')).toBeInTheDocument();
  });
});
