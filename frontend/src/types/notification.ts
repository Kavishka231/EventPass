import type { IsoInstant, Uuid } from './shared';

export interface NotificationResponse {
  id: Uuid;
  type: string;
  title: string;
  message: string;
  createdAt: IsoInstant;
  readAt: IsoInstant | null;
}

export interface UnreadCountResponse {
  unreadCount: number;
}
