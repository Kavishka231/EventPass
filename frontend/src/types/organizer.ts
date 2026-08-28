import type { BookingStatus } from './booking';
import type { IsoInstant, MoneyAmount, Uuid } from './shared';

export interface OrganizerEventBookingResponse {
  id: Uuid;
  reference: string;
  eventId: Uuid;
  customerId: Uuid;
  customerEmail: string;
  customerFirstName: string;
  customerLastName: string;
  status: BookingStatus;
  totalAmount: MoneyAmount;
  currency: string;
  eventSeatIds: Uuid[];
  createdAt: IsoInstant;
}
