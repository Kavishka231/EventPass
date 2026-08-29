import { Link } from 'react-router-dom';

import { Container, Grid, Section } from '../components/layout';
import { EventCard, EventCardSkeleton } from '../components/events';
import { ErrorState } from '../components/ui';
import { useEvents } from '../features/events';

const upcomingFrom = new Date().toISOString();

export function HomePage() {
  const featured = useEvents({
    page: 0,
    size: 3,
    sort: 'startDateTime,asc',
    startDate: upcomingFrom,
  });

  return (
    <>
      <Section className="home-hero">
        <Container>
          <div className="home-hero-grid">
            <div className="home-hero-copy">
              <p className="discovery-eyebrow">Your next live moment</p>
              <h1>Events worth showing up for.</h1>
              <p>
                Discover published experiences, find the right date and place,
                and keep every ticket confidently within EventPass.
              </p>
              <div className="home-actions">
                <Link
                  className="button link-button"
                  data-size="large"
                  data-variant="primary"
                  to="/events"
                >
                  Explore events
                </Link>
                <Link
                  className="button link-button"
                  data-size="large"
                  data-variant="outline"
                  to="/register"
                >
                  Create account
                </Link>
              </div>
            </div>
            <div className="home-hero-art" aria-hidden="true">
              <span className="hero-date">EVENTPASS</span>
              <strong>Live</strong>
              <small>Music · Culture · Sport</small>
            </div>
          </div>
        </Container>
      </Section>

      <Section>
        <Container>
          <div className="section-heading-row">
            <div>
              <p className="discovery-eyebrow">Coming up</p>
              <h2>Make plans that feel memorable</h2>
            </div>
            <Link to="/events">View all events</Link>
          </div>

          {featured.isPending ? (
            <Grid aria-label="Loading upcoming events">
              {[0, 1, 2].map((item) => (
                <EventCardSkeleton key={item} />
              ))}
            </Grid>
          ) : featured.isError ? (
            <ErrorState
              title="Upcoming events are unavailable"
              description="We couldn't load events right now. Please try again."
              actionLabel="Try again"
              onAction={() => void featured.refetch()}
            />
          ) : featured.data.content.length === 0 ? (
            <div className="home-empty">
              <h3>New experiences are on their way</h3>
              <p>Check back soon for newly published EventPass events.</p>
            </div>
          ) : (
            <Grid>
              {featured.data.content.map((event) => (
                <EventCard event={event} key={event.id} />
              ))}
            </Grid>
          )}
        </Container>
      </Section>

      <Section className="home-confidence">
        <Container>
          <div className="confidence-grid">
            <div>
              <span>01</span>
              <h3>Published events only</h3>
              <p>Discovery reflects the backend's authoritative event state.</p>
            </div>
            <div>
              <span>02</span>
              <h3>Clear event information</h3>
              <p>Dates, venues, cities, and categories stay easy to compare.</p>
            </div>
            <div>
              <span>03</span>
              <h3>Ready for secure booking</h3>
              <p>
                Account and ticket experiences remain connected in one place.
              </p>
            </div>
          </div>
        </Container>
      </Section>
    </>
  );
}
