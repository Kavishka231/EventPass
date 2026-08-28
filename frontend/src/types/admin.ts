import type { BookingStatus } from './booking';
import type {
  IsoInstant,
  MoneyAmount,
  UserRole,
  UserStatus,
  Uuid,
} from './shared';

export interface AdminUserResponse {
  id: Uuid;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  status: UserStatus;
  createdAt: IsoInstant;
}

export interface UpdateUserRequest {
  role: UserRole;
  status: UserStatus;
}

export interface AdminStatisticsResponse {
  users: number;
  events: number;
  venues: number;
  bookings: number;
  confirmedBookings: number;
}

export interface AdminBookingResponse {
  id: Uuid;
  reference: string;
  eventId: Uuid;
  eventName: string;
  customerId: Uuid;
  customerEmail: string;
  status: BookingStatus;
  totalAmount: MoneyAmount;
  currency: string;
  eventSeatIds: Uuid[];
  createdAt: IsoInstant;
}

export interface AdminBookingSearchParameters {
  eventId?: Uuid;
  status?: BookingStatus;
  page?: number;
  size?: number;
  sort?: string | readonly string[];
}
