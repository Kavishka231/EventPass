import type { IsoInstant, MoneyAmount, Uuid } from './shared';

export type BookingStatus =
  'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED' | 'FAILED';

export interface CreateBookingRequest {
  eventId: Uuid;
  eventSeatIds: Uuid[];
  paymentToken: string;
}

export interface BookingResponse {
  id: Uuid;
  reference: string;
  eventId: Uuid;
  status: BookingStatus;
  totalAmount: MoneyAmount;
  currency: string;
  eventSeatIds: Uuid[];
  createdAt: IsoInstant;
}
