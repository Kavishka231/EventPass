import type { IsoInstant, MoneyAmount, Uuid } from './shared';

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED';
export type SeatType = 'REGULAR' | 'PREMIUM' | 'VIP';
export type EventSeatAvailability = 'AVAILABLE' | 'HELD' | 'SOLD' | 'BLOCKED';

export interface EventRequest {
  name: string;
  description: string;
  category: string;
  startDateTime: IsoInstant;
  endDateTime: IsoInstant;
  venueId: Uuid;
  status: EventStatus;
}

export interface EventResponse extends EventRequest {
  id: Uuid;
  venueName: string;
  city: string;
}

export interface EventSearchParameters {
  category?: string;
  city?: string;
  startDate?: IsoInstant;
  endDate?: IsoInstant;
  page?: number;
  size?: number;
  sort?: string | readonly string[];
}

export interface EventSeatResponse {
  id: Uuid;
  section: string;
  row: string;
  number: string;
  type: SeatType;
  price: MoneyAmount;
  availability: EventSeatAvailability;
}

export interface EventSeatConfigurationRequest {
  seatId: Uuid;
  price: MoneyAmount;
  blocked: boolean;
}

export interface VenueRequest {
  name: string;
  address: string;
  city: string;
  capacity: number;
}

export interface VenueResponse extends VenueRequest {
  id: Uuid;
}

export interface CreateSeatRequest {
  section: string;
  row: string;
  number: string;
  type: SeatType;
}

export interface SeatDefinitionResponse extends CreateSeatRequest {
  id: Uuid;
  venueId: Uuid;
}
