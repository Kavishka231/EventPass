import type {
  AdminBookingSearchParameters,
  EventSearchParameters,
  PaginationParameters,
  Uuid,
} from '../../types';

export const queryKeys = {
  events: {
    all: ['events'] as const,
    lists: () => [...queryKeys.events.all, 'list'] as const,
    list: (filters: EventSearchParameters) =>
      [...queryKeys.events.lists(), filters] as const,
    detail: (eventId: Uuid) =>
      [...queryKeys.events.all, 'detail', eventId] as const,
    seats: (eventId: Uuid) =>
      [...queryKeys.events.detail(eventId), 'seats'] as const,
  },
  venues: {
    all: ['venues'] as const,
    list: (pagination: PaginationParameters) =>
      [...queryKeys.venues.all, 'list', pagination] as const,
    detail: (venueId: Uuid) =>
      [...queryKeys.venues.all, 'detail', venueId] as const,
  },
  bookings: {
    all: ['bookings'] as const,
    list: (pagination: PaginationParameters) =>
      [...queryKeys.bookings.all, 'list', pagination] as const,
    detail: (bookingId: Uuid) =>
      [...queryKeys.bookings.all, 'detail', bookingId] as const,
  },
  tickets: {
    all: ['tickets'] as const,
    list: (pagination: PaginationParameters) =>
      [...queryKeys.tickets.all, 'list', pagination] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    lists: () => [...queryKeys.notifications.all, 'list'] as const,
    list: (pagination: PaginationParameters) =>
      [...queryKeys.notifications.lists(), pagination] as const,
    unreadCount: () =>
      [...queryKeys.notifications.all, 'unread-count'] as const,
  },
  organizer: {
    all: ['organizer'] as const,
    eventBookings: (eventId: Uuid, pagination: PaginationParameters) =>
      [
        ...queryKeys.organizer.all,
        'events',
        eventId,
        'bookings',
        pagination,
      ] as const,
  },
  admin: {
    all: ['admin'] as const,
    statistics: () => [...queryKeys.admin.all, 'statistics'] as const,
    users: (pagination: PaginationParameters) =>
      [...queryKeys.admin.all, 'users', pagination] as const,
    bookings: (filters: AdminBookingSearchParameters) =>
      [...queryKeys.admin.all, 'bookings', filters] as const,
  },
} as const;
