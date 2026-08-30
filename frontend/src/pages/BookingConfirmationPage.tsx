import { Link, useNavigate, useParams } from 'react-router-dom';

import { BookingOverview } from '../components/booking';
import { Container, Section } from '../components/layout';
import { ErrorState, Skeleton, SuccessState } from '../components/ui';
import { useBooking } from '../features/bookings';
import { useEvent, useEventSeats } from '../features/events';
import { isApiError } from '../services/api';

export function BookingConfirmationPage() {
  const navigate = useNavigate();
  const bookingId = useParams().bookingId ?? '';
  const booking = useBooking(bookingId);
  const event = useEvent(booking.data?.eventId ?? '', Boolean(booking.data));
  const seats = useEventSeats(
    booking.data?.eventId ?? '',
    Boolean(booking.data),
  );

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

  return (
    <Section className="booking-management-section">
      <Container size="large">
        <div className="booking-confirmation-state">
          {booking.data.status === 'CONFIRMED' ? (
            <SuccessState
              title="Your booking is confirmed"
              description="Your seats are secured and digital tickets are available in EventPass."
            />
          ) : (
            <div className="booking-confirmation-message">
              <p className="discovery-eyebrow">Booking received</p>
              <h1>Booking status: {booking.data.status.toLowerCase()}</h1>
              <p>
                Review the current backend status and booking details below.
              </p>
            </div>
          )}
        </div>
        <BookingOverview
          booking={booking.data}
          event={event.data}
          seats={seats.data}
          showTicketAction
        />
        <Link className="back-link" to="/bookings">
          View all bookings
        </Link>
      </Container>
    </Section>
  );
}
