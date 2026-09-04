import type { BookingStatus } from './booking';
import type { IsoInstant, MoneyAmount, Uuid } from './shared';
import type {
  EventRequest,
  EventResponse,
  SeatType,
  VenueResponse,
} from './event';
import type { PaginatedResponse, PaginationParameters } from './pagination';

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

export interface OrganizerInventoryOption {
  seatId: Uuid;
  section: string;
  row: string;
  number: string;
  type: SeatType;
  price: MoneyAmount | null;
  blocked: boolean;
  configured: boolean;
}

export interface OrganizerServiceContract {
  listEvents(
    parameters: PaginationParameters,
  ): Promise<PaginatedResponse<EventResponse>>;
  getEvent(eventId: Uuid): Promise<EventResponse>;
  createEvent(request: EventRequest): Promise<EventResponse>;
  updateEvent(eventId: Uuid, request: EventRequest): Promise<EventResponse>;
  cancelEvent(eventId: Uuid): Promise<void>;
  listVenues(
    parameters: PaginationParameters,
  ): Promise<PaginatedResponse<VenueResponse>>;
}
