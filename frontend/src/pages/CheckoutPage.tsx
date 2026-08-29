import { type FormEvent, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { seatDisplayName } from '../components/booking';
import { Container, Section } from '../components/layout';
import {
  Alert,
  Button,
  ErrorState,
  FieldError,
  Input,
  Label,
  Panel,
  Skeleton,
} from '../components/ui';
import { useCreateBooking } from '../features/bookings';
import { useEvent, useEventSeats } from '../features/events';
import { isApiError } from '../services/api';

interface CheckoutSelection {
  eventId: string;
  eventSeatIds: string[];
}

function checkoutSelection(value: unknown): CheckoutSelection | null {
  if (typeof value !== 'object' || value === null) return null;
  const state = value as Record<string, unknown>;
  const eventId = state.eventId;
  const eventSeatIds = state.eventSeatIds;
  if (
    typeof eventId !== 'string' ||
    !Array.isArray(eventSeatIds) ||
    eventSeatIds.length < 1 ||
    eventSeatIds.length > 10 ||
    eventSeatIds.some((id) => typeof id !== 'string') ||
    new Set(eventSeatIds).size !== eventSeatIds.length
  ) {
    return null;
  }
  return { eventId, eventSeatIds: eventSeatIds as string[] };
}

function idempotencyKey() {
  if (!globalThis.crypto?.randomUUID) {
    throw new Error(
      'Secure booking identifiers are unavailable in this browser.',
    );
  }
  return globalThis.crypto.randomUUID();
}

function CheckoutSkeleton() {
  return (
    <Section aria-label="Loading checkout">
      <Container size="medium">
        <div className="checkout-skeleton">
          <Skeleton className="event-detail-title-skeleton" />
          <Skeleton />
          <Skeleton />
          <Skeleton />
        </div>
      </Container>
    </Section>
  );
}

export function CheckoutPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const selection = checkoutSelection(location.state);
  const eventQuery = useEvent(selection?.eventId ?? '', Boolean(selection));
  const seatsQuery = useEventSeats(
    selection?.eventId ?? '',
    Boolean(selection),
  );
  const booking = useCreateBooking();
  const attemptKey = useRef<string | null>(null);
  const submitting = useRef(false);
  const [paymentToken, setPaymentToken] = useState('');
  const [paymentError, setPaymentError] = useState<string>();

  if (!selection) {
    return (
      <Section>
        <Container size="small">
          <ErrorState
            title="No seats selected"
            description="Checkout state is unavailable. Return to an event and choose seats before continuing."
            actionLabel="Browse events"
            onAction={() => void navigate('/events')}
          />
        </Container>
      </Section>
    );
  }

  if (eventQuery.isPending || seatsQuery.isPending) return <CheckoutSkeleton />;

  if (eventQuery.isError || seatsQuery.isError) {
    return (
      <Section>
        <Container size="small">
          <ErrorState
            title="Checkout details are unavailable"
            description="The event or selected inventory could not be confirmed. Return to seat selection and try again."
            actionLabel="Return to seat selection"
            onAction={() =>
              void navigate(`/events/${selection.eventId}/seats`, {
                replace: true,
              })
            }
          />
        </Container>
      </Section>
    );
  }

  const event = eventQuery.data;
  const seatIds = new Set(selection.eventSeatIds);
  const selectedSeats = seatsQuery.data.filter((seat) => seatIds.has(seat.id));
  const validSelection =
    selectedSeats.length === selection.eventSeatIds.length &&
    selectedSeats.every((seat) => seat.availability === 'AVAILABLE');
  const estimatedTotal = selectedSeats.reduce(
    (total, seat) => total + seat.price,
    0,
  );
  const priceFormatter = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'LKR',
    maximumFractionDigits: 2,
  });
  const startsAt = new Date(event.startDateTime);
  const selectedEventId = selection.eventId;
  const selectedEventSeatIds = selection.eventSeatIds;

  async function submit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    if (submitting.current || booking.isPending || !validSelection) return;
    if (!paymentToken.trim()) {
      setPaymentError('Enter a sandbox payment token.');
      return;
    }
    setPaymentError(undefined);
    submitting.current = true;
    try {
      attemptKey.current ??= idempotencyKey();
      const response = await booking.mutateAsync({
        idempotencyKey: attemptKey.current,
        request: {
          eventId: selectedEventId,
          eventSeatIds: selectedEventSeatIds,
          paymentToken: paymentToken.trim(),
        },
      });
      await navigate(`/bookings/${response.id}/confirmation`, {
        replace: true,
        state: { booking: response },
      });
    } catch {
      // The normalized mutation error is rendered below and the key remains stable.
    } finally {
      submitting.current = false;
    }
  }

  const apiError = isApiError(booking.error) ? booking.error : null;
  const seatConflict = apiError?.code === 'SEAT_UNAVAILABLE';
  const eventConflict = apiError?.code === 'EVENT_NOT_BOOKABLE';
  const paymentFailed = apiError?.code === 'PAYMENT_FAILED';
  const outcomeUnknown = apiError?.code === 'PAYMENT_OUTCOME_UNKNOWN';
  const keyConflict = apiError?.code === 'IDEMPOTENCY_PAYLOAD_MISMATCH';
  const rateLimited = apiError?.kind === 'rate-limit';
  const uncertainTransport =
    apiError?.kind === 'network' ||
    apiError?.kind === 'timeout' ||
    apiError?.kind === 'server';

  function startNewPaymentAttempt() {
    attemptKey.current = null;
    booking.reset();
    setPaymentError(undefined);
  }

  return (
    <Section className="checkout-section">
      <Container>
        <Link className="back-link" to={`/events/${event.id}/seats`}>
          <span aria-hidden="true">←</span> Back to seat selection
        </Link>
        <header className="checkout-heading">
          <p className="discovery-eyebrow">Secure sandbox checkout</p>
          <h1>Complete your booking</h1>
          <p>
            {event.name} ·{' '}
            {startsAt.toLocaleDateString(undefined, { dateStyle: 'long' })} ·{' '}
            {event.venueName}, {event.city}
          </p>
        </header>

        {!validSelection ? (
          <Alert title="Your seat selection changed" tone="warning">
            One or more selected seats are no longer available. Return to seat
            selection and choose again.
          </Alert>
        ) : null}

        {seatConflict || eventConflict ? (
          <Alert title="Booking could not continue" tone="error">
            One or more seats or the event are no longer available. Return to
            seat selection to refresh the inventory.
          </Alert>
        ) : paymentFailed ? (
          <Alert title="Payment could not be completed" tone="error">
            Your booking was not confirmed. Use another sandbox payment token to
            start a new payment attempt.
          </Alert>
        ) : outcomeUnknown ? (
          <Alert title="We're confirming your payment" tone="warning">
            The booking status is being finalized. Retry this same attempt to
            retrieve the backend's current booking state; do not start another
            payment.
          </Alert>
        ) : keyConflict ? (
          <Alert title="Booking attempt conflict" tone="error">
            This attempt no longer matches its original booking request. Return
            to seat selection and begin again.
          </Alert>
        ) : rateLimited ? (
          <Alert title="Too many booking attempts" tone="warning">
            Wait a moment, then retry this same booking attempt.
          </Alert>
        ) : uncertainTransport ? (
          <Alert title="Booking result not received" tone="warning">
            The request may have reached EventPass. Retry to safely check the
            same attempt; its idempotency key will not change.
          </Alert>
        ) : booking.isError ? (
          <Alert title="Booking could not be completed" tone="error">
            No success was confirmed. Review the details or return to seat
            selection.
          </Alert>
        ) : null}

        <div className="checkout-layout">
          <div className="checkout-order">
            <section aria-labelledby="checkout-seats-title">
              <h2 id="checkout-seats-title">Selected seats</h2>
              <ul className="checkout-seat-list">
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
              <div className="checkout-estimate">
                <span>Estimated total</span>
                <strong>{priceFormatter.format(estimatedTotal)}</strong>
              </div>
              <p>
                This estimate is not sent as booking data. EventPass calculates
                the authoritative total from locked backend inventory.
              </p>
            </section>
          </div>

          <aside className="checkout-payment" aria-labelledby="payment-title">
            <Panel>
              <form onSubmit={(event) => void submit(event)}>
                <p className="discovery-eyebrow">Payment</p>
                <h2 id="payment-title">Sandbox payment</h2>
                <div className="checkout-payment-field">
                  <Label htmlFor="payment-token">Sandbox payment token</Label>
                  <Input
                    id="payment-token"
                    name="paymentToken"
                    type="text"
                    autoComplete="off"
                    maxLength={200}
                    value={paymentToken}
                    disabled={booking.isPending || Boolean(attemptKey.current)}
                    aria-invalid={Boolean(paymentError)}
                    aria-describedby={
                      paymentError
                        ? 'payment-token-guidance payment-token-error'
                        : 'payment-token-guidance'
                    }
                    onChange={(event) => setPaymentToken(event.target.value)}
                  />
                  <p id="payment-token-guidance">
                    This project uses a mock provider. `tok_success` succeeds,
                    `tok_fail` declines, and `tok_unknown` simulates an
                    ambiguous provider result. Never enter real card details.
                  </p>
                  {paymentError ? (
                    <FieldError id="payment-token-error">
                      {paymentError}
                    </FieldError>
                  ) : null}
                </div>
                <Button
                  className="booking-action"
                  type="submit"
                  size="large"
                  loading={booking.isPending}
                  loadingLabel="Processing your booking..."
                  disabled={!validSelection || paymentFailed}
                >
                  Complete booking
                </Button>
              </form>
              {paymentFailed ? (
                <Button variant="outline" onClick={startNewPaymentAttempt}>
                  Try another sandbox token
                </Button>
              ) : null}
              {seatConflict || eventConflict || keyConflict ? (
                <Link
                  className="button link-button booking-action"
                  data-size="medium"
                  data-variant="outline"
                  to={`/events/${event.id}/seats`}
                >
                  Return to seat selection
                </Link>
              ) : null}
            </Panel>
          </aside>
        </div>
      </Container>
    </Section>
  );
}
