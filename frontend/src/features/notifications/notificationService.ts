import {
  apiClient,
  emptyResponseDecoder,
  objectResponseDecoder,
  paginatedResponseDecoder,
} from '../../services/api';
import type {
  NotificationResponse,
  PaginationParameters,
  UnreadCountResponse,
} from '../../types';

function requiredString(object: Record<string, unknown>, field: string) {
  const value = object[field];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Expected ${field} to be a non-empty string.`);
  }
  return value;
}

function instant(value: unknown, field: string, nullable = false) {
  if (value === null && nullable) return null;
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) {
    throw new Error(`Expected ${field} to be an ISO date-time.`);
  }
  return value;
}

function notificationResponseDecoder(value: unknown): NotificationResponse {
  const object = objectResponseDecoder(value);
  return {
    id: requiredString(object, 'id'),
    type: requiredString(object, 'type'),
    title: requiredString(object, 'title'),
    message: requiredString(object, 'message'),
    createdAt: instant(object.createdAt, 'createdAt') as string,
    readAt: instant(object.readAt, 'readAt', true),
  };
}

function unreadCountResponseDecoder(value: unknown): UnreadCountResponse {
  const object = objectResponseDecoder(value);
  const unreadCount = object.unreadCount;
  if (
    typeof unreadCount !== 'number' ||
    !Number.isSafeInteger(unreadCount) ||
    unreadCount < 0
  ) {
    throw new Error('Expected unreadCount to be a non-negative integer.');
  }
  return { unreadCount };
}

export const notificationService = {
  async list(parameters: PaginationParameters) {
    const response = await apiClient.get(
      '/notifications',
      paginatedResponseDecoder(notificationResponseDecoder),
      {
        query: {
          page: parameters.page,
          size: parameters.size,
          sort: parameters.sort,
        },
      },
    );
    return response.data;
  },

  async unreadCount() {
    const response = await apiClient.get(
      '/notifications/unread-count',
      unreadCountResponseDecoder,
    );
    return response.data;
  },

  async markRead(notificationId: string) {
    await apiClient.patch(
      `/notifications/${notificationId}/read`,
      undefined,
      emptyResponseDecoder,
    );
  },
};
