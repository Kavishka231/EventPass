import type { IsoInstant, Uuid } from './shared';

export type TicketStatus = 'ACTIVE' | 'USED' | 'CANCELLED';

export interface TicketResponse {
  id: Uuid;
  ticketNumber: string;
  bookingId: Uuid;
  eventSeatId: Uuid;
  qrToken: string;
  status: TicketStatus;
  issuedAt: IsoInstant;
}

export interface ValidateTicketRequest {
  qrToken: string;
  eventId: Uuid;
}

export interface TicketValidationResponse {
  ticketId: Uuid;
  ticketNumber: string;
  eventId: Uuid;
  eventSeatId: Uuid;
  status: TicketStatus;
  eventName: string;
  eventStartDateTime: IsoInstant;
}

export interface TicketRedemptionResponse {
  ticketId: Uuid;
  ticketNumber: string;
  eventId: Uuid;
  status: TicketStatus;
  usedAt: IsoInstant;
}
