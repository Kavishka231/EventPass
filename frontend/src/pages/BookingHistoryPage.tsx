import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { BookingStatusBadge, paymentOutcome } from '../components/booking';
import { Container, Section } from '../components/layout';
import { Button, EmptyState, ErrorState, Skeleton } from '../components/ui';
import { useBookings } from '../features/bookings';
import type { CustomerBookingSummary } from '../types';

const PAGE_SIZE = 20;

function requestedPage(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

function BookingHistoryCard({ booking }: { booking: CustomerBookingSummary }) {
  const startsAt = new Date(booking.event.startDateTime);
  const total = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: booking.currency,
    maximumFractionDigits: 2,
  }).format(booking.totalAmount);

  return (
    <Link className="booking-history-card" to={`/bookings/${booking.id}`}>
      <div className="booking-history-card-heading">
        <div>
          <p className="discovery-eyebrow">{booking.reference}</p>
          <h2>{booking.event.name}</h2>
        </div>
        <BookingStatusBadge status={booking.status} />
      </div>
      <div className="booking-history-meta">
        <span>
          {startsAt.toLocaleString(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short',
          })}
        </span>
        <span>
          {booking.venue.name}, {booking.venue.city}
        </span>
      </div>
      <div className="booking-history-summary">
        <span>
          {booking.seatCount} {booking.seatCount === 1 ? 'seat' : 'seats'}
        </span>
        <span>{paymentOutcome(booking.status)}</span>
        <strong>{total}</strong>
      </div>
    </Link>
  );
}

export function BookingHistoryPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const currentPage = requestedPage(searchParams.get('page'));
  const bookings = useBookings(currentPage - 1, PAGE_SIZE);

  function selectPage(page: number) {
    const next = new URLSearchParams(searchParams);
    if (page <= 1) next.delete('page');
    else next.set('page', String(page));
    setSearchParams(next);
    document.querySelector('#booking-history')?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  }

  return (
    <Section className="booking-management-section">
      <Container>
        <header className="booking-management-heading" id="booking-history">
          <p className="discovery-eyebrow">Your EventPass</p>
          <h1>Bookings</h1>
          <p>Review upcoming experiences and your complete booking history.</p>
        </header>

        {bookings.isPending ? (
          <div className="booking-history-list" aria-label="Loading bookings">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton className="booking-history-skeleton" key={index} />
            ))}
          </div>
        ) : bookings.isError ? (
          <ErrorState
            title="Bookings couldn't be loaded"
            description="Check your connection and try again."
            actionLabel="Try again"
            onAction={() => void bookings.refetch()}
          />
        ) : bookings.data.content.length === 0 ? (
          <EmptyState
            title="No bookings yet"
            description="When you reserve an event, its booking will appear here."
            actionLabel="Browse events"
            onAction={() => void navigate('/events')}
          />
        ) : (
          <div
            className={`booking-history-list${bookings.isFetching ? ' results-updating' : ''}`}
          >
            {bookings.data.content.map((booking) => (
              <BookingHistoryCard booking={booking} key={booking.id} />
            ))}
          </div>
        )}

        {bookings.data && bookings.data.totalPages > 1 ? (
          <nav className="pagination" aria-label="Booking history pages">
            <Button
              variant="outline"
              disabled={bookings.data.first || bookings.isFetching}
              onClick={() => selectPage(currentPage - 1)}
            >
              Previous
            </Button>
            <span aria-live="polite">
              Page {currentPage} of {bookings.data.totalPages}
            </span>
            <Button
              variant="outline"
              disabled={bookings.data.last || bookings.isFetching}
              onClick={() => selectPage(currentPage + 1)}
            >
              Next
            </Button>
          </nav>
        ) : null}
      </Container>
    </Section>
  );
}
