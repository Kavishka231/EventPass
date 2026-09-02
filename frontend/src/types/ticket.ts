import type { IsoInstant, Uuid } from './shared';

export type TicketStatus = 'ACTIVE' | 'USED' | 'CANCELLED';

export interface TicketResponse {
  id: Uuid;
  ticketNumber: string;
  bookingId: Uuid;
  eventSeatId: Uuid;
  qrToken: string | null;
  status: TicketStatus;
  issuedAt: IsoInstant;
  usedAt: IsoInstant | null;
  bookingReference: string;
  event: {
    id: Uuid;
    name: string;
    startDateTime: IsoInstant;
    endDateTime: IsoInstant;
  };
  venue: { id: Uuid; name: string; address: string; city: string };
  seat: {
    id: Uuid;
    section: string;
    row: string;
    number: string;
    type: 'REGULAR' | 'PREMIUM' | 'VIP';
  };
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
