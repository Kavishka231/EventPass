import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import {
  AdminBookingPage,
  AdminDashboardPage,
  AdminEventsPage,
  AdminUsersPage,
  AdminVenuePage,
} from '../pages/AdminPages';
import { SessionContext } from '../features/session/SessionContext';
import { page } from './fixtures';
import { renderWithApp } from './render';
import { server } from './server';

const admin = {
  id: 'admin-1',
  email: 'admin@example.com',
  firstName: 'Admin',
  lastName: 'User',
  role: 'ADMIN',
  status: 'ACTIVE',
  createdAt: '2030-01-01T00:00:00Z',
};
const customer = {
  ...admin,
  id: 'customer-1',
  email: 'customer@example.com',
  firstName: 'Customer',
  role: 'CUSTOMER',
};
const venue = {
  id: 'venue-1',
  name: 'City Hall',
  address: 'Main Street',
  city: 'Colombo',
  capacity: 100,
};
const event = {
  id: 'event-1',
  name: 'Summit',
  description: 'Event',
  category: 'Business',
  startDateTime: '2031-01-01T10:00:00Z',
  endDateTime: '2031-01-01T12:00:00Z',
  status: 'DRAFT',
  venueId: venue.id,
  venueName: venue.name,
  city: venue.city,
};
const booking = {
  id: 'booking-1',
  reference: 'EVP-1',
  eventId: event.id,
  eventName: event.name,
  customerId: customer.id,
  customerEmail: customer.email,
  status: 'CONFIRMED',
  totalAmount: 5000,
  currency: 'LKR',
  eventSeatIds: ['seat-1'],
  createdAt: '2030-01-01T00:00:00Z',
};

describe('administrator workspace', () => {
  it('shows the operational dashboard totals', async () => {
    server.use(
      http.get('*/api/v1/admin/statistics', () =>
        HttpResponse.json({
          users: 3,
          events: 2,
          venues: 1,
          bookings: 4,
          confirmedBookings: 2,
        }),
      ),
    );
    renderWithApp(<AdminDashboardPage />, '/admin');
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByText('2 confirmed')).toBeInTheDocument();
  });
  it('prevents self lifecycle editing and submits another user change', async () => {
    const updated = vi.fn();
    server.use(
      http.get('*/api/v1/admin/users', () =>
        HttpResponse.json(page([admin, customer])),
      ),
      http.put(`*/api/v1/admin/users/${customer.id}`, async ({ request }) => {
        updated(await request.json());
        return HttpResponse.json({ ...customer, role: 'ORGANIZER' });
      }),
    );
    renderWithApp(
      <SessionContext.Provider
        value={{
          status: 'authenticated',
          session: { userId: admin.id, role: 'ADMIN' },
          sessionExpired: false,
          authenticate: () => Promise.resolve(),
          logout: () => Promise.resolve(),
        }}
      >
        <AdminUsersPage />
      </SessionContext.Provider>,
      '/admin/users',
    );
    const user = userEvent.setup();
    expect(
      await screen.findByLabelText(`Role for ${admin.email}`),
    ).toBeDisabled();
    await user.selectOptions(
      screen.getByLabelText(`Role for ${customer.email}`),
      'ORGANIZER',
    );
    await user.click(screen.getAllByRole('button', { name: 'Save' })[1]);
    await waitFor(() =>
      expect(updated).toHaveBeenCalledWith({
        role: 'ORGANIZER',
        status: 'ACTIVE',
      }),
    );
  });
  it('lists physical seats and creates a seat for a venue', async () => {
    const created = vi.fn();
    server.use(
      http.get(`*/api/v1/venues/${venue.id}`, () => HttpResponse.json(venue)),
      http.get(`*/api/v1/admin/venues/${venue.id}/seats`, () =>
        HttpResponse.json(
          page([
            {
              id: 'seat-1',
              venueId: venue.id,
              section: 'A',
              row: '1',
              number: '1',
              type: 'REGULAR',
            },
          ]),
        ),
      ),
      http.post(`*/api/v1/venues/${venue.id}/seats`, async ({ request }) => {
        created(await request.json());
        return HttpResponse.json([], { status: 201 });
      }),
    );
    renderWithApp(
      <Routes>
        <Route path="/admin/venues/:venueId" element={<AdminVenuePage />} />
      </Routes>,
      `/admin/venues/${venue.id}`,
    );
    const user = userEvent.setup();
    expect((await screen.findAllByText('REGULAR')).length).toBeGreaterThan(1);
    await user.type(screen.getByLabelText('Section'), 'B');
    await user.type(screen.getByLabelText('Row'), '2');
    await user.type(screen.getByLabelText('Seat number'), '10');
    await user.click(screen.getByRole('button', { name: 'Add seat' }));
    await waitFor(() =>
      expect(created).toHaveBeenCalledWith([
        { section: 'B', row: '2', number: '10', type: 'REGULAR' },
      ]),
    );
  });
  it('lists every event state and cancels an eligible booking', async () => {
    server.use(
      http.get('*/api/v1/admin/events', () => HttpResponse.json(page([event]))),
    );
    renderWithApp(<AdminEventsPage />, '/admin/events');
    expect(await screen.findByText('DRAFT')).toBeInTheDocument();
    const cancelled = vi.fn();
    server.use(
      http.get(`*/api/v1/admin/bookings/${booking.id}`, () =>
        HttpResponse.json(booking),
      ),
      http.post(`*/api/v1/admin/bookings/${booking.id}/cancel`, () => {
        cancelled();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWithApp(
      <Routes>
        <Route
          path="/admin/bookings/:bookingId"
          element={<AdminBookingPage />}
        />
      </Routes>,
      `/admin/bookings/${booking.id}`,
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'Cancel booking' }),
    );
    await waitFor(() => expect(cancelled).toHaveBeenCalled());
  });
});
