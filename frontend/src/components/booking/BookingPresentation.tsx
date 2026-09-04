import { Link } from 'react-router-dom';

import type { BookingStatus, CustomerBookingDetails } from '../../types';
import { Badge, Panel } from '../ui';
import { paymentOutcome, refundOutcome } from './bookingStatus';

const statusTone = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'neutral',
  EXPIRED: 'neutral',
  FAILED: 'error',
} as const;

const statusLabel = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  FAILED: 'Failed',
} as const;

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  return <Badge tone={statusTone[status]}>{statusLabel[status]}</Badge>;
}

function money(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

interface BookingOverviewProps {
  booking: CustomerBookingDetails;
  showTicketAction?: boolean;
}

export function BookingOverview({
  booking,
  showTicketAction = false,
}: BookingOverviewProps) {
  const startsAt = new Date(booking.event.startDateTime);

  return (
    <div className="booking-overview">
      <Panel className="booking-overview-main">
        <div className="booking-overview-heading">
          <div>
            <p className="discovery-eyebrow">Booking {booking.reference}</p>
            <h2>{booking.event.name}</h2>
          </div>
          <BookingStatusBadge status={booking.status} />
        </div>

        <dl className="booking-facts">
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
              {booking.venue.name}, {booking.venue.city}
            </dd>
          </div>
          <div>
            <dt>Booked</dt>
            <dd>
              {new Date(booking.createdAt).toLocaleString(undefined, {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}
            </dd>
          </div>
          <div>
            <dt>Total paid</dt>
            <dd>{money(booking.totalAmount, booking.currency)}</dd>
          </div>
          <div>
            <dt>Payment</dt>
            <dd>{booking.payment?.status ?? paymentOutcome(booking.status)}</dd>
          </div>
          <div>
            <dt>Refund</dt>
            <dd>{booking.refund?.status ?? refundOutcome(booking.status)}</dd>
          </div>
        </dl>

        <section
          className="booking-seat-details"
          aria-labelledby="booked-seats"
        >
          <h3 id="booked-seats">Seats</h3>
          <ul>
            {booking.seats.map((seat) => {
              return (
                <li key={seat.eventSeatId}>
                  <strong>{`${seat.section} · Row ${seat.row} · Seat ${seat.number}`}</strong>
                  <span>{seat.type.toLowerCase()}</span>
                </li>
              );
            })}
          </ul>
        </section>

        <div className="booking-overview-actions">
          <Link
            className="button link-button"
            data-size="medium"
            data-variant="outline"
            to={`/bookings/${booking.id}`}
          >
            View booking
          </Link>
          {showTicketAction && booking.status === 'CONFIRMED' ? (
            <Link
              className="button link-button"
              data-size="medium"
              data-variant="primary"
              state={{ bookingId: booking.id }}
              to="/tickets"
            >
              View tickets
            </Link>
          ) : null}
        </div>
      </Panel>
    </div>
  );
}
