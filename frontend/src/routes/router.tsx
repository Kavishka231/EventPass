import { createBrowserRouter } from 'react-router-dom';

import { CustomerShell, PublicShell, WorkspaceShell } from '../layouts';
import { LoginPage, RegistrationPage } from '../pages/AuthenticationPage';
import {
  AdminBookingPage,
  AdminBookingsPage,
  AdminDashboardPage,
  AdminEventPage,
  AdminEventsPage,
  AdminUsersPage,
  AdminVenuePage,
  AdminVenuesPage,
} from '../pages/AdminPages';
import { BookingConfirmationPage } from '../pages/BookingConfirmationPage';
import { BookingDetailsPage } from '../pages/BookingDetailsPage';
import { BookingHistoryPage } from '../pages/BookingHistoryPage';
import { AdmissionPage } from '../pages/AdmissionPage';
import { CheckoutPage } from '../pages/CheckoutPage';
import { EventDiscoveryPage } from '../pages/EventDiscoveryPage';
import { EventDetailsPage } from '../pages/EventDetailsPage';
import { HomePage } from '../pages/HomePage';
import { NotificationsPage } from '../pages/NotificationsPage';
import {
  OrganizerBookingsPage,
  OrganizerDashboardPage,
  OrganizerEventFormPage,
  OrganizerEventsPage,
  OrganizerInventoryPage,
} from '../pages/OrganizerPages';
import { SeatSelectionPage } from '../pages/SeatSelectionPage';
import { TicketsPage } from '../pages/TicketsPage';
import { NotFoundPage, UnauthorizedPage } from '../pages/SystemPage';
import { RouteGuard } from './RouteGuard';

const organizerNavigation = [
  { label: 'Overview', to: '/organizer', end: true },
  { label: 'My events', to: '/organizer/events' },
  { label: 'Admission', to: '/admission' },
];

const administratorNavigation = [
  { label: 'Overview', to: '/admin', end: true },
  { label: 'Users', to: '/admin/users' },
  { label: 'Venues', to: '/admin/venues' },
  { label: 'Events', to: '/admin/events' },
  { label: 'Bookings', to: '/admin/bookings' },
  { label: 'Admission', to: '/admission' },
];

const admissionNavigation = [
  { label: 'Admission', to: '/admission', end: true },
];

export const applicationRoutes = {
  public: ['/', '/events', '/login', '/register'],
  customer: [
    '/checkout',
    '/bookings',
    '/bookings/:bookingId',
    '/bookings/:bookingId/confirmation',
    '/tickets',
    '/notifications',
  ],
  organizer: [
    '/organizer',
    '/organizer/events',
    '/organizer/events/new',
    '/organizer/events/:eventId/edit',
    '/organizer/events/:eventId/inventory',
    '/organizer/events/:eventId/bookings',
  ],
  administrator: [
    '/admin',
    '/admin/users',
    '/admin/venues',
    '/admin/venues/:venueId',
    '/admin/events',
    '/admin/events/:eventId',
    '/admin/bookings',
    '/admin/bookings/:bookingId',
  ],
  admission: ['/admission'],
  system: ['/unauthorized', '*'],
} as const;

export const router = createBrowserRouter([
  {
    element: <PublicShell />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'events',
        element: <EventDiscoveryPage />,
      },
      {
        path: 'events/:eventId',
        element: <EventDetailsPage />,
      },
      {
        path: 'events/:eventId/seats',
        element: <SeatSelectionPage />,
      },
      {
        path: 'login',
        element: <LoginPage />,
      },
      {
        path: 'register',
        element: <RegistrationPage />,
      },
      { path: 'unauthorized', element: <UnauthorizedPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    element: (
      <RouteGuard roles={['CUSTOMER']}>
        <CustomerShell />
      </RouteGuard>
    ),
    children: [
      {
        path: 'checkout',
        element: <CheckoutPage />,
      },
      {
        path: 'bookings',
        element: <BookingHistoryPage />,
      },
      {
        path: 'bookings/:bookingId',
        element: <BookingDetailsPage />,
      },
      {
        path: 'bookings/:bookingId/confirmation',
        element: <BookingConfirmationPage />,
      },
      {
        path: 'tickets',
        element: <TicketsPage />,
      },
      {
        path: 'notifications',
        element: <NotificationsPage />,
      },
    ],
  },
  {
    element: (
      <RouteGuard roles={['ORGANIZER']}>
        <WorkspaceShell label="Organizer" navigation={organizerNavigation} />
      </RouteGuard>
    ),
    children: [
      {
        path: 'organizer',
        element: <OrganizerDashboardPage />,
      },
      {
        path: 'organizer/events',
        element: <OrganizerEventsPage />,
      },
      {
        path: 'organizer/events/new',
        element: <OrganizerEventFormPage />,
      },
      {
        path: 'organizer/events/:eventId/edit',
        element: <OrganizerEventFormPage />,
      },
      {
        path: 'organizer/events/:eventId/inventory',
        element: <OrganizerInventoryPage />,
      },
      {
        path: 'organizer/events/:eventId/bookings',
        element: <OrganizerBookingsPage />,
      },
    ],
  },
  {
    element: (
      <RouteGuard roles={['ADMIN']}>
        <WorkspaceShell
          label="Administration"
          navigation={administratorNavigation}
        />
      </RouteGuard>
    ),
    children: [
      {
        path: 'admin',
        element: <AdminDashboardPage />,
      },
      {
        path: 'admin/users',
        element: <AdminUsersPage />,
      },
      {
        path: 'admin/venues',
        element: <AdminVenuesPage />,
      },
      {
        path: 'admin/venues/:venueId',
        element: <AdminVenuePage />,
      },
      {
        path: 'admin/events',
        element: <AdminEventsPage />,
      },
      {
        path: 'admin/events/:eventId',
        element: <AdminEventPage />,
      },
      {
        path: 'admin/bookings',
        element: <AdminBookingsPage />,
      },
      {
        path: 'admin/bookings/:bookingId',
        element: <AdminBookingPage />,
      },
    ],
  },
  {
    element: (
      <RouteGuard roles={['ORGANIZER', 'ADMIN']}>
        <WorkspaceShell label="Admission" navigation={admissionNavigation} />
      </RouteGuard>
    ),
    children: [
      {
        path: 'admission',
        element: <AdmissionPage />,
      },
    ],
  },
]);
