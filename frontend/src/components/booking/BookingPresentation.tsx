import { Link } from 'react-router-dom';

import type {
  BookingResponse,
  BookingStatus,
  EventResponse,
  EventSeatResponse,
} from '../../types';
import { Badge, Panel } from '../ui';
import { paymentOutcome, refundOutcome } from './bookingStatus';
import { seatDisplayName } from './seatPresentation';

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
  booking: BookingResponse;
  event?: EventResponse;
  seats?: EventSeatResponse[];
  showTicketAction?: boolean;
}

export function BookingOverview({
  booking,
  event,
  seats,
  showTicketAction = false,
}: BookingOverviewProps) {
  const seatsById = new Map(seats?.map((seat) => [seat.id, seat]));
  const startsAt = event ? new Date(event.startDateTime) : null;

  return (
    <div className="booking-overview">
      <Panel className="booking-overview-main">
        <div className="booking-overview-heading">
          <div>
            <p className="discovery-eyebrow">Booking {booking.reference}</p>
            <h2>{event?.name ?? 'Event booking'}</h2>
          </div>
          <BookingStatusBadge status={booking.status} />
        </div>

        <dl className="booking-facts">
          <div>
            <dt>Date and time</dt>
            <dd>
              {startsAt
                ? startsAt.toLocaleString(undefined, {
                    dateStyle: 'long',
                    timeStyle: 'short',
                  })
                : 'Event information unavailable'}
            </dd>
          </div>
          <div>
            <dt>Venue</dt>
            <dd>
              {event ? `${event.venueName}, ${event.city}` : 'Unavailable'}
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
            <dd>{paymentOutcome(booking.status)}</dd>
          </div>
          <div>
            <dt>Refund</dt>
            <dd>{refundOutcome(booking.status)}</dd>
          </div>
        </dl>

        <section
          className="booking-seat-details"
          aria-labelledby="booked-seats"
        >
          <h3 id="booked-seats">Seats</h3>
          <ul>
            {booking.eventSeatIds.map((seatId) => {
              const seat = seatsById.get(seatId);
              return (
                <li key={seatId}>
                  <strong>
                    {seat ? seatDisplayName(seat) : 'Reserved seat'}
                  </strong>
                  <span>{seat ? seat.type.toLowerCase() : seatId}</span>
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
