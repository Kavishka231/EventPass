import { useLocation, useSearchParams } from 'react-router-dom';

import { Container, Section } from '../components/layout';
import { TicketCard } from '../components/tickets';
import { Button, EmptyState, ErrorState, Skeleton } from '../components/ui';
import { useTickets } from '../features/tickets';

const PAGE_SIZE = 20;

function requestedPage(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

function highlightedBooking(value: unknown) {
  if (typeof value !== 'object' || value === null) return null;
  const bookingId = (value as Record<string, unknown>).bookingId;
  return typeof bookingId === 'string' ? bookingId : null;
}

export function TicketsPage() {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const currentPage = requestedPage(searchParams.get('page'));
  const tickets = useTickets(currentPage - 1, PAGE_SIZE);
  const bookingId = highlightedBooking(location.state);
  const orderedTickets = tickets.data
    ? [...tickets.data.content].sort((left, right) => {
        if (left.bookingId === bookingId) return -1;
        if (right.bookingId === bookingId) return 1;
        return 0;
      })
    : [];

  function selectPage(page: number) {
    const next = new URLSearchParams(searchParams);
    if (page <= 1) next.delete('page');
    else next.set('page', String(page));
    setSearchParams(next);
    document.querySelector('#ticket-list')?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  }

  return (
    <Section className="ticket-section">
      <Container>
        <header className="booking-management-heading" id="ticket-list">
          <p className="discovery-eyebrow">Secure admission</p>
          <h1>Digital tickets</h1>
          <p>
            Present active tickets at admission. Ticket status is always
            confirmed by EventPass staff against the backend.
          </p>
        </header>

        {tickets.isPending ? (
          <div className="ticket-list" aria-label="Loading tickets">
            {Array.from({ length: 2 }, (_, index) => (
              <Skeleton className="ticket-skeleton" key={index} />
            ))}
          </div>
        ) : tickets.isError ? (
          <ErrorState
            title="Tickets couldn't be loaded"
            description="Check your connection and try again."
            actionLabel="Try again"
            onAction={() => void tickets.refetch()}
          />
        ) : orderedTickets.length === 0 ? (
          <EmptyState
            title="No digital tickets yet"
            description="Tickets are issued after a booking is confirmed."
          />
        ) : (
          <div
            className={`ticket-list${tickets.isFetching ? ' results-updating' : ''}`}
          >
            {orderedTickets.map((ticket) => (
              <TicketCard
                highlighted={ticket.bookingId === bookingId}
                key={ticket.id}
                ticket={ticket}
              />
            ))}
          </div>
        )}

        {tickets.data && tickets.data.totalPages > 1 ? (
          <nav className="pagination" aria-label="Digital ticket pages">
            <Button
              variant="outline"
              disabled={tickets.data.first || tickets.isFetching}
              onClick={() => selectPage(currentPage - 1)}
            >
              Previous
            </Button>
            <span aria-live="polite">
              Page {currentPage} of {tickets.data.totalPages}
            </span>
            <Button
              variant="outline"
              disabled={tickets.data.last || tickets.isFetching}
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
