import { useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { BookingOverview } from '../components/booking';
import { Container, Section } from '../components/layout';
import { Alert, Button, Dialog, ErrorState, Skeleton } from '../components/ui';
import { useBooking, useCancelBooking } from '../features/bookings';
import { isApiError } from '../services/api';

export function BookingDetailsPage() {
  const navigate = useNavigate();
  const bookingId = useParams().bookingId ?? '';
  const booking = useBooking(bookingId);
  const cancellation = useCancelBooking();
  const submitting = useRef(false);
  const [confirming, setConfirming] = useState(false);

  if (booking.isPending) {
    return (
      <Section>
        <Container size="medium" className="booking-page-skeleton">
          <Skeleton />
          <Skeleton />
          <Skeleton />
        </Container>
      </Section>
    );
  }

  if (booking.isError) {
    const missing =
      isApiError(booking.error) && booking.error.code === 'BOOKING_NOT_FOUND';
    return (
      <Section>
        <Container size="small">
          <ErrorState
            title={missing ? 'Booking not found' : "Booking couldn't be loaded"}
            description={
              missing
                ? 'This booking does not exist or is not available to your account.'
                : 'Check your connection and try again.'
            }
            actionLabel={missing ? 'View bookings' : 'Try again'}
            onAction={() => {
              if (missing) void navigate('/bookings');
              else void booking.refetch();
            }}
          />
        </Container>
      </Section>
    );
  }

  const startsAt = new Date(booking.data.event.startDateTime);
  const cancellationWindowOpen =
    booking.data.status === 'CONFIRMED' &&
    startsAt.getTime() > Date.now() + 24 * 60 * 60 * 1000;
  const cancellationError = isApiError(cancellation.error)
    ? cancellation.error
    : null;

  async function cancelBooking() {
    if (submitting.current || cancellation.isPending) return;
    submitting.current = true;
    try {
      await cancellation.mutateAsync(bookingId);
      setConfirming(false);
    } catch {
      setConfirming(false);
    } finally {
      submitting.current = false;
    }
  }

  return (
    <Section className="booking-management-section">
      <Container size="large">
        <Link className="back-link" to="/bookings">
          &larr; Back to bookings
        </Link>
        <header className="booking-management-heading">
          <p className="discovery-eyebrow">Booking details</p>
          <h1>{booking.data.reference}</h1>
          <p>
            Review your booking, payment outcome, seats, and cancellation
            eligibility.
          </p>
        </header>

        {booking.data.status === 'CANCELLED' ? (
          <Alert title="Booking cancelled" tone="success">
            The booking is cancelled, its seats were released, and the backend
            completed the refund workflow.
          </Alert>
        ) : cancellationError ? (
          <Alert
            title={
              cancellationError.code === 'REFUND_OUTCOME_UNKNOWN' ||
              cancellationError.code === 'REFUND_PENDING'
                ? 'Refund requires confirmation'
                : 'Cancellation could not be completed'
            }
            tone={
              cancellationError.code === 'REFUND_OUTCOME_UNKNOWN' ||
              cancellationError.code === 'REFUND_PENDING'
                ? 'warning'
                : 'error'
            }
          >
            {cancellationError.code === 'BOOKING_NOT_CANCELLABLE'
              ? 'This booking is no longer eligible for customer cancellation.'
              : cancellationError.code === 'PAYMENT_NOT_REFUNDABLE'
                ? 'No refundable successful payment is available for this booking.'
                : cancellationError.code === 'REFUND_OUTCOME_UNKNOWN' ||
                    cancellationError.code === 'REFUND_PENDING'
                  ? 'The backend is reconciling the existing refund. Do not submit another cancellation.'
                  : 'Please try again later or contact support with the request ID shown by the service.'}
          </Alert>
        ) : null}

        <BookingOverview booking={booking.data} showTicketAction />

        <div className="booking-cancellation-panel">
          <div>
            <h2>Cancellation</h2>
            <p>
              Customer cancellation is available for confirmed bookings more
              than 24 hours before the event. The backend remains authoritative.
            </p>
          </div>
          {cancellationWindowOpen ? (
            <Button
              variant="danger"
              disabled={cancellation.isPending}
              onClick={() => setConfirming(true)}
            >
              Cancel booking
            </Button>
          ) : (
            <span className="booking-cancellation-unavailable">
              Cancellation unavailable
            </span>
          )}
        </div>

        <Dialog
          open={confirming}
          title="Cancel this booking?"
          description="This action requests a refund and releases the booked seats when the backend completes it."
          onClose={() => {
            if (!cancellation.isPending) setConfirming(false);
          }}
          footer={
            <>
              <Button
                variant="ghost"
                disabled={cancellation.isPending}
                onClick={() => setConfirming(false)}
              >
                Keep booking
              </Button>
              <Button
                variant="danger"
                loading={cancellation.isPending}
                loadingLabel="Cancelling booking..."
                onClick={() => void cancelBooking()}
              >
                Confirm cancellation
              </Button>
            </>
          }
        >
          <p>
            Booking <strong>{booking.data.reference}</strong> for{' '}
            {booking.data.seats.length}{' '}
            {booking.data.seats.length === 1 ? 'seat' : 'seats'} will be
            cancelled.
          </p>
        </Dialog>
      </Container>
    </Section>
  );
}
