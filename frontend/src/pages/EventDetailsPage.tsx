import { Link, useNavigate, useParams } from 'react-router-dom';

import { Container, Section } from '../components/layout';
import { Badge, Button, ErrorState, Panel, Skeleton } from '../components/ui';
import { useEvent, useEventSeats } from '../features/events';
import { isApiError } from '../services/api';
import type { EventSeatResponse, SeatType } from '../types';

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  weekday: 'long',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});
const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit',
});
const priceFormatter = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: 'LKR',
  maximumFractionDigits: 2,
});

const seatTypeLabels: Record<SeatType, string> = {
  REGULAR: 'Regular',
  PREMIUM: 'Premium',
  VIP: 'VIP',
};

interface PriceBand {
  type: SeatType;
  minimum: number;
  maximum: number;
}

function priceBands(seats: EventSeatResponse[]): PriceBand[] {
  const grouped = new Map<SeatType, number[]>();
  for (const seat of seats) {
    const prices = grouped.get(seat.type) ?? [];
    prices.push(seat.price);
    grouped.set(seat.type, prices);
  }
  return [...grouped.entries()].map(([type, prices]) => ({
    type,
    minimum: Math.min(...prices),
    maximum: Math.max(...prices),
  }));
}

function EventDetailsSkeleton() {
  return (
    <Section aria-label="Loading event details">
      <Container>
        <div className="event-detail-skeleton">
          <Skeleton className="event-detail-hero-skeleton" />
          <div>
            <Skeleton className="skeleton-label" />
            <Skeleton className="event-detail-title-skeleton" />
            <Skeleton />
            <Skeleton />
          </div>
        </div>
      </Container>
    </Section>
  );
}

export function EventDetailsPage() {
  const navigate = useNavigate();
  const { eventId = '' } = useParams();
  const eventQuery = useEvent(eventId);
  const seatsQuery = useEventSeats(eventId, eventQuery.isSuccess);

  if (eventQuery.isPending) return <EventDetailsSkeleton />;

  if (eventQuery.isError) {
    const notFound =
      isApiError(eventQuery.error) && eventQuery.error.status === 404;
    return (
      <Section>
        <Container size="small">
          <ErrorState
            title={notFound ? 'Event not found' : "We couldn't load this event"}
            description={
              notFound
                ? 'This event may have been removed or is no longer publicly available.'
                : 'Check your connection or try again in a moment.'
            }
            actionLabel={notFound ? 'Back to events' : 'Try again'}
            onAction={() => {
              if (notFound) void navigate('/events');
              else void eventQuery.refetch();
            }}
          />
        </Container>
      </Section>
    );
  }

  const event = eventQuery.data;
  const startsAt = new Date(event.startDateTime);
  const endsAt = new Date(event.endDateTime);
  const seats = seatsQuery.data ?? [];
  const available = seats.filter(
    (seat) => seat.availability === 'AVAILABLE',
  ).length;
  const allSold =
    seats.length > 0 && seats.every((seat) => seat.availability === 'SOLD');
  const futureEvent = startsAt.getTime() > Date.now();
  const bookable =
    futureEvent &&
    seatsQuery.isSuccess &&
    available > 0 &&
    event.status === 'PUBLISHED';
  const availabilityLabel = seatsQuery.isPending
    ? 'Checking availability'
    : seatsQuery.isError
      ? 'Availability unavailable'
      : available > 0
        ? `${available} ${available === 1 ? 'seat' : 'seats'} available`
        : allSold
          ? 'Sold out'
          : 'Currently unavailable';
  const unavailableAction = !futureEvent
    ? 'Sales closed'
    : allSold
      ? 'Sold out'
      : 'Unavailable';

  return (
    <>
      <Section className="event-detail-section">
        <Container>
          <Link className="back-link" to="/events">
            <span aria-hidden="true">←</span> Back to events
          </Link>

          <div
            className="event-detail-hero"
            role="img"
            aria-label={`${event.name} editorial event artwork`}
          >
            <span>{event.category}</span>
            <strong aria-hidden="true">{startsAt.getDate()}</strong>
            <small aria-hidden="true">
              {startsAt.toLocaleString(undefined, { month: 'long' })}
            </small>
          </div>

          <div className="event-detail-layout">
            <div className="event-detail-main">
              <header className="event-detail-heading">
                <Badge tone="accent">{event.category}</Badge>
                <h1>{event.name}</h1>
                <p>
                  {dateFormatter.format(startsAt)} ·{' '}
                  {timeFormatter.format(startsAt)}–
                  {timeFormatter.format(endsAt)}
                </p>
              </header>

              <section
                className="event-detail-block"
                aria-labelledby="about-event"
              >
                <h2 id="about-event">About this event</h2>
                <p className="event-description">{event.description}</p>
              </section>

              <section
                className="event-detail-block"
                aria-labelledby="event-venue"
              >
                <h2 id="event-venue">Venue</h2>
                <div className="venue-summary">
                  <strong>{event.venueName}</strong>
                  <span>{event.city}</span>
                </div>
              </section>

              <section
                className="event-detail-block"
                aria-labelledby="ticket-prices"
              >
                <h2 id="ticket-prices">Ticket pricing</h2>
                {seatsQuery.isPending ? (
                  <div
                    className="price-list"
                    aria-label="Loading ticket prices"
                  >
                    <Skeleton />
                    <Skeleton />
                  </div>
                ) : seatsQuery.isError ? (
                  <p className="inventory-message">
                    Ticket pricing is temporarily unavailable.
                  </p>
                ) : (
                  <dl className="price-list">
                    {priceBands(seats).map((band) => (
                      <div key={band.type}>
                        <dt>{seatTypeLabels[band.type]}</dt>
                        <dd>
                          {band.minimum === band.maximum
                            ? priceFormatter.format(band.minimum)
                            : `${priceFormatter.format(band.minimum)}–${priceFormatter.format(band.maximum)}`}
                        </dd>
                      </div>
                    ))}
                  </dl>
                )}
                <p className="inventory-note">
                  Prices and availability are supplied by EventPass inventory.
                </p>
              </section>
            </div>

            <aside
              className="booking-summary"
              aria-labelledby="booking-summary-title"
            >
              <Panel>
                <p className="discovery-eyebrow">Event access</p>
                <h2 id="booking-summary-title">Plan your visit</h2>
                <dl>
                  <div>
                    <dt>Date</dt>
                    <dd>{dateFormatter.format(startsAt)}</dd>
                  </div>
                  <div>
                    <dt>Venue</dt>
                    <dd>{event.venueName}</dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>{availabilityLabel}</dd>
                  </div>
                </dl>
                {bookable ? (
                  <Link
                    className="button link-button booking-action"
                    data-size="large"
                    data-variant="primary"
                    to={`/events/${event.id}/seats`}
                  >
                    Select seats
                  </Link>
                ) : (
                  <Button className="booking-action" disabled size="large">
                    {seatsQuery.isPending
                      ? 'Checking availability…'
                      : unavailableAction}
                  </Button>
                )}
                {seatsQuery.dataUpdatedAt > 0 ? (
                  <p className="availability-updated" role="status">
                    Availability snapshot updated at{' '}
                    {timeFormatter.format(seatsQuery.dataUpdatedAt)}.
                  </p>
                ) : null}
              </Panel>
            </aside>
          </div>
        </Container>
      </Section>

      <div className="mobile-booking-action">
        {bookable ? (
          <Link
            className="button link-button"
            data-size="large"
            data-variant="primary"
            to={`/events/${event.id}/seats`}
          >
            Select seats · {available} available
          </Link>
        ) : (
          <Button disabled size="large">
            {seatsQuery.isPending
              ? 'Checking availability…'
              : unavailableAction}
          </Button>
        )}
      </div>
    </>
  );
}
