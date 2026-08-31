import { createBrowserRouter } from 'react-router-dom';

import { PublicShell, WorkspaceShell } from '../layouts';
import { LoginPage, RegistrationPage } from '../pages/AuthenticationPage';
import { BookingConfirmationPage } from '../pages/BookingConfirmationPage';
import { BookingDetailsPage } from '../pages/BookingDetailsPage';
import { BookingHistoryPage } from '../pages/BookingHistoryPage';
import { CheckoutPage } from '../pages/CheckoutPage';
import { EventDiscoveryPage } from '../pages/EventDiscoveryPage';
import { EventDetailsPage } from '../pages/EventDetailsPage';
import { HomePage } from '../pages/HomePage';
import { SeatSelectionPage } from '../pages/SeatSelectionPage';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { NotFoundPage, UnauthorizedPage } from '../pages/SystemPage';
import { RouteGuard } from './RouteGuard';

const customerNavigation = [
  { label: 'Bookings', to: '/bookings' },
  { label: 'Tickets', to: '/tickets' },
  { label: 'Notifications', to: '/notifications' },
];

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
        <WorkspaceShell label="My EventPass" navigation={customerNavigation} />
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
        element: (
          <PlaceholderPage
            group="Customer"
            title="My tickets"
            description="Secure digital ticket presentation will be implemented later."
          />
        ),
      },
      {
        path: 'notifications',
        element: (
          <PlaceholderPage
            group="Customer"
            title="Notifications"
            description="Customer notification management will be implemented later."
          />
        ),
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
        element: (
          <PlaceholderPage
            group="Organizer"
            title="Organizer overview"
            description="Organizer reporting and activity will be implemented later."
          />
        ),
      },
      {
        path: 'organizer/events',
        element: (
          <PlaceholderPage
            group="Organizer"
            title="My events"
            description="Organizer-owned event management will be implemented later."
          />
        ),
      },
      {
        path: 'organizer/events/new',
        element: (
          <PlaceholderPage
            group="Organizer"
            title="Create event"
            description="Event creation will be implemented in the organizer feature."
          />
        ),
      },
      {
        path: 'organizer/events/:eventId/edit',
        element: (
          <PlaceholderPage
            group="Organizer"
            title="Edit event"
            description="Owned-event editing will be implemented in the organizer feature."
          />
        ),
      },
      {
        path: 'organizer/events/:eventId/inventory',
        element: (
          <PlaceholderPage
            group="Organizer"
            title="Event inventory"
            description="Seat pricing and blocking will be implemented later."
          />
        ),
      },
      {
        path: 'organizer/events/:eventId/bookings',
        element: (
          <PlaceholderPage
            group="Organizer"
            title="Event booking report"
            description="Owned-event booking reports will be implemented later."
          />
        ),
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
        element: (
          <PlaceholderPage
            group="Administrator"
            title="Platform overview"
            description="Platform statistics will be implemented in the administrator feature."
          />
        ),
      },
      {
        path: 'admin/users',
        element: (
          <PlaceholderPage
            group="Administrator"
            title="User management"
            description="Administrator user lifecycle controls will be implemented later."
          />
        ),
      },
      {
        path: 'admin/venues',
        element: (
          <PlaceholderPage
            group="Administrator"
            title="Venue management"
            description="Venue administration will be implemented later."
          />
        ),
      },
      {
        path: 'admin/venues/:venueId',
        element: (
          <PlaceholderPage
            group="Administrator"
            title="Venue details"
            description="Venue and physical-seat management will be implemented later."
          />
        ),
      },
      {
        path: 'admin/events',
        element: (
          <PlaceholderPage
            group="Administrator"
            title="Event management"
            description="Administrator event operations will be implemented later."
          />
        ),
      },
      {
        path: 'admin/bookings',
        element: (
          <PlaceholderPage
            group="Administrator"
            title="Booking management"
            description="Platform booking management will be implemented later."
          />
        ),
      },
      {
        path: 'admin/bookings/:bookingId',
        element: (
          <PlaceholderPage
            group="Administrator"
            title="Booking details"
            description="Administrative booking detail and cancellation will be implemented later."
          />
        ),
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
        element: (
          <PlaceholderPage
            group="Admission"
            title="Ticket admission"
            description="Secure validation and redemption scanning will be implemented later."
          />
        ),
      },
    ],
  },
]);
