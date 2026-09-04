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

export interface BookingEventSummary {
  id: Uuid;
  name: string;
  startDateTime: IsoInstant;
  endDateTime: IsoInstant | null;
}

export interface BookingVenueSummary {
  id: Uuid;
  name: string;
  address: string | null;
  city: string;
}

export interface CustomerBookingSummary {
  id: Uuid;
  reference: string;
  status: BookingStatus;
  totalAmount: MoneyAmount;
  currency: string;
  seatCount: number;
  createdAt: IsoInstant;
  event: BookingEventSummary;
  venue: BookingVenueSummary;
}

export interface BookedSeat {
  eventSeatId: Uuid;
  seatId: Uuid;
  section: string;
  row: string;
  number: string;
  type: 'REGULAR' | 'PREMIUM' | 'VIP';
  unitPrice: MoneyAmount;
}

export interface CustomerBookingDetails extends Omit<
  CustomerBookingSummary,
  'seatCount'
> {
  updatedAt: IsoInstant;
  expiresAt: IsoInstant;
  seats: BookedSeat[];
  payment: null | {
    status: string;
    attemptedAt: IsoInstant | null;
    completedAt: IsoInstant | null;
  };
  refund: null | {
    status: string;
    amount: MoneyAmount;
    attemptedAt: IsoInstant | null;
    completedAt: IsoInstant | null;
  };
}
