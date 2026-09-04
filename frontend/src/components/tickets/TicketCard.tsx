import QRCode from 'react-qr-code';

import { Badge } from '../ui';
import type { TicketResponse } from '../../types';

const ticketTone = {
  ACTIVE: 'success',
  USED: 'neutral',
  CANCELLED: 'error',
} as const;

const ticketLabel = {
  ACTIVE: 'Active',
  USED: 'Used',
  CANCELLED: 'Cancelled',
} as const;

export function TicketCard({
  highlighted = false,
  ticket,
}: {
  highlighted?: boolean;
  ticket: TicketResponse;
}) {
  const startsAt = new Date(ticket.event.startDateTime);

  return (
    <article
      className="digital-ticket"
      data-highlighted={highlighted || undefined}
      data-status={ticket.status.toLowerCase()}
      id={`ticket-${ticket.id}`}
    >
      <div className="digital-ticket-content">
        <header className="digital-ticket-heading">
          <div>
            <p className="discovery-eyebrow">EventPass digital ticket</p>
            <h2>{ticket.event.name}</h2>
          </div>
          <Badge tone={ticketTone[ticket.status]}>
            {ticketLabel[ticket.status]}
          </Badge>
        </header>

        <dl className="ticket-facts">
          <div>
            <dt>Date and time</dt>
            <dd>
              {startsAt.toLocaleString(undefined, {
                dateStyle: 'long',
                timeStyle: 'short',
              })}
            </dd>
          </div>
          <div>
            <dt>Venue</dt>
            <dd>
              {ticket.venue.name}, {ticket.venue.city}
            </dd>
          </div>
          <div>
            <dt>Seat</dt>
            <dd>
              {ticket.seat.section} · Row {ticket.seat.row} · Seat{' '}
              {ticket.seat.number}
            </dd>
          </div>
          <div>
            <dt>Ticket number</dt>
            <dd>{ticket.ticketNumber}</dd>
          </div>
          <div>
            <dt>Booking reference</dt>
            <dd>{ticket.bookingReference}</dd>
          </div>
          <div>
            <dt>Issued</dt>
            <dd>{new Date(ticket.issuedAt).toLocaleDateString()}</dd>
          </div>
        </dl>

        {ticket.status === 'ACTIVE' && ticket.qrToken ? (
          <div className="ticket-admission-guidance">
            <strong>Ready for admission</strong>
            <p>
              Present this ticket to authorized venue staff at the event
              entrance.
            </p>
          </div>
        ) : ticket.status === 'USED' ? (
          <div className="ticket-state-notice" role="status">
            <strong>Admission already completed</strong>
            <p>
              This ticket has already been redeemed and cannot be used again.
            </p>
          </div>
        ) : (
          <div className="ticket-state-notice" role="status">
            <strong>Ticket invalid</strong>
            <p>This ticket was cancelled and is not valid for admission.</p>
          </div>
        )}
      </div>

      <aside className="ticket-qr-panel" aria-label="Ticket admission code">
        {ticket.status === 'ACTIVE' && ticket.qrToken ? (
          <>
            <div className="ticket-qr" aria-hidden="true">
              <QRCode
                value={ticket.qrToken}
                bgColor="#ffffff"
                fgColor="#171614"
                level="M"
                size={220}
              />
            </div>
            <p>Scan for admission</p>
          </>
        ) : (
          <div className="ticket-qr-unavailable" aria-hidden="true">
            <span>{ticket.status === 'USED' ? 'Used' : 'Invalid'}</span>
          </div>
        )}
      </aside>
    </article>
  );
}
