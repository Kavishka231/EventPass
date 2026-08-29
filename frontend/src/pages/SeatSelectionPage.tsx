import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { SeatLegend, SeatMap, seatDisplayName } from '../components/booking';
import { Container, Section } from '../components/layout';
import {
  Alert,
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Panel,
  Skeleton,
} from '../components/ui';
import { useEvent, useEventSeats } from '../features/events';
import { useSession } from '../features/session';
import { isApiError } from '../services/api';
import type { EventSeatResponse } from '../types';

const MAXIMUM_SEATS = 10;
const priceFormatter = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: 'LKR',
  maximumFractionDigits: 2,
});

function SeatMapSkeleton() {
  return (
    <div className="seat-map-skeleton" aria-label="Loading seat map">
      <Skeleton className="seat-stage-skeleton" />
      {[0, 1, 2, 3].map((row) => (
        <div className="seat-skeleton-row" key={row}>
          {Array.from({ length: 8 }, (_, seat) => (
            <Skeleton key={seat} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function SeatSelectionPage() {
  const navigate = useNavigate();
  const { eventId = '' } = useParams();
  const sessionController = useSession();
  const eventQuery = useEvent(eventId);
  const seatsQuery = useEventSeats(eventId, eventQuery.isSuccess);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [selectionMessage, setSelectionMessage] = useState<string>();

  const selectedSeats = useMemo(() => {
    const seats = seatsQuery.data ?? [];
    return seats.filter(
      (seat) => selectedIds.has(seat.id) && seat.availability === 'AVAILABLE',
    );
  }, [seatsQuery.data, selectedIds]);

  useEffect(() => {
    if (!seatsQuery.data || selectedIds.size === 0) return;
    const availableIds = new Set(
      seatsQuery.data
        .filter((seat) => seat.availability === 'AVAILABLE')
        .map((seat) => seat.id),
    );
    const removed = [...selectedIds].filter((id) => !availableIds.has(id));
    if (removed.length === 0) return;
    setSelectedIds(
      new Set([...selectedIds].filter((id) => availableIds.has(id))),
    );
    setSelectionMessage(
      removed.length === 1
        ? 'A selected seat is no longer available. Please choose another.'
        : 'Some selected seats are no longer available. Please choose others.',
    );
  }, [seatsQuery.data, selectedIds]);

  function toggleSeat(seat: EventSeatResponse) {
    setSelectionMessage(undefined);
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(seat.id)) next.delete(seat.id);
      else if (next.size < MAXIMUM_SEATS) next.add(seat.id);
      else
        setSelectionMessage(
          `You can select up to ${MAXIMUM_SEATS} seats per booking.`,
        );
      return next;
    });
  }

  async function continueToCheckout() {
    if (selectedSeats.length === 0) return;
    if (
      sessionController.status !== 'authenticated' ||
      !sessionController.session
    ) {
      const returnTo = `/events/${eventId}/seats`;
      await navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`);
      return;
    }
    await navigate('/checkout', {
      state: {
        eventId,
        eventSeatIds: selectedSeats.map((seat) => seat.id),
      },
    });
  }

  if (eventQuery.isPending) {
    return (
      <Section>
        <Container>
          <SeatMapSkeleton />
        </Container>
      </Section>
    );
  }

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
                ? 'This event is no longer publicly available.'
                : 'Check your connection or try again in a moment.'
            }
            actionLabel={notFound ? 'Back to events' : 'Try again'}
            onAction={() =>
              notFound ? void navigate('/events') : void eventQuery.refetch()
            }
          />
        </Container>
      </Section>
    );
  }

  const event = eventQuery.data;
  const eventIsFuture = new Date(event.startDateTime).getTime() > Date.now();
  const availableSeats =
    seatsQuery.data?.filter((seat) => seat.availability === 'AVAILABLE') ?? [];
  const estimatedTotal = selectedSeats.reduce(
    (total, seat) => total + seat.price,
    0,
  );

  return (
    <Section className="seat-selection-section">
      <Container>
        <Link className="back-link" to={`/events/${event.id}`}>
          <span aria-hidden="true">←</span> Back to event
        </Link>
        <header className="seat-selection-heading">
          <div>
            <Badge tone="accent">{event.category}</Badge>
            <h1>Choose your seats</h1>
            <p>
              {event.name} · {event.venueName}, {event.city}
            </p>
          </div>
          <SeatLegend />
        </header>

        {selectionMessage ? (
          <Alert title="Selection updated" tone="warning">
            {selectionMessage}
          </Alert>
        ) : null}

        {!eventIsFuture ? (
          <ErrorState
            title="Seat selection is closed"
            description="This event has started or is no longer available for booking."
            actionLabel="Back to event"
            onAction={() => void navigate(`/events/${event.id}`)}
          />
        ) : seatsQuery.isPending ? (
          <SeatMapSkeleton />
        ) : seatsQuery.isError ? (
          <ErrorState
            title="Seat inventory is unavailable"
            description="We couldn't retrieve the current seat snapshot. No selection has been held."
            actionLabel="Try again"
            onAction={() => void seatsQuery.refetch()}
          />
        ) : availableSeats.length === 0 ? (
          <EmptyState
            title="No seats are currently available"
            description="Please return to the event page to explore another event."
            actionLabel="Back to event"
            onAction={() => void navigate(`/events/${event.id}`)}
          />
        ) : (
          <div className="seat-selection-layout">
            <div className="seat-map-panel">
              <SeatMap
                seats={seatsQuery.data}
                selectedIds={selectedIds}
                onToggle={toggleSeat}
              />
              <p className="seat-selection-note">
                Selecting here does not hold a seat. Availability is confirmed
                by the backend during checkout.
              </p>
            </div>

            <aside
              className="selection-summary"
              aria-labelledby="selection-summary-title"
            >
              <Panel>
                <p className="discovery-eyebrow">Your selection</p>
                <h2 id="selection-summary-title">Selected seats</h2>
                {selectedSeats.length === 0 ? (
                  <p className="selection-empty">
                    Choose available seats from the map.
                  </p>
                ) : (
                  <ul className="selected-seat-list">
                    {selectedSeats.map((seat) => (
                      <li key={seat.id}>
                        <span>
                          <strong>{seatDisplayName(seat)}</strong>
                          <small>{seat.type.toLowerCase()}</small>
                        </span>
                        <span>{priceFormatter.format(seat.price)}</span>
                      </li>
                    ))}
                  </ul>
                )}
                <dl className="selection-totals">
                  <div>
                    <dt>Tickets</dt>
                    <dd>{selectedSeats.length}</dd>
                  </div>
                  <div>
                    <dt>Estimated total</dt>
                    <dd>{priceFormatter.format(estimatedTotal)}</dd>
                  </div>
                </dl>
                <Button
                  className="booking-action"
                  size="large"
                  disabled={selectedSeats.length === 0}
                  onClick={() => void continueToCheckout()}
                >
                  {selectedSeats.length === 0
                    ? 'Select seats'
                    : sessionController.status === 'authenticated'
                      ? 'Continue to checkout'
                      : 'Sign in to continue'}
                </Button>
                <p className="selection-disclaimer">
                  Estimated from current backend prices. Final price and
                  availability are confirmed during booking.
                </p>
              </Panel>
            </aside>
          </div>
        )}
      </Container>

      {eventIsFuture && seatsQuery.isSuccess && availableSeats.length > 0 ? (
        <div className="mobile-selection-summary">
          <div>
            <strong>
              {selectedSeats.length}{' '}
              {selectedSeats.length === 1 ? 'seat' : 'seats'} selected
            </strong>
            <span>{priceFormatter.format(estimatedTotal)} estimated</span>
          </div>
          <Button
            disabled={selectedSeats.length === 0}
            onClick={() => void continueToCheckout()}
          >
            {selectedSeats.length === 0 ? 'Select seats' : 'Continue'}
          </Button>
        </div>
      ) : null}
    </Section>
  );
}
