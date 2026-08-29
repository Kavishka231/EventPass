import { Badge, Card } from '../ui';
import type { EventResponse } from '../../types';
import { Link } from 'react-router-dom';

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
});
const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit',
});

export function EventCard({ event }: { event: EventResponse }) {
  const startsAt = new Date(event.startDateTime);

  return (
    <Card className="event-card">
      <div className="event-card-visual" aria-hidden="true">
        <span>{startsAt.getDate()}</span>
        <small>{startsAt.toLocaleString(undefined, { month: 'short' })}</small>
      </div>
      <div className="event-card-content">
        <Badge tone="accent">{event.category}</Badge>
        <div>
          <h2>{event.name}</h2>
          <p className="event-card-description">{event.description}</p>
        </div>
        <dl className="event-card-meta">
          <div>
            <dt>Date</dt>
            <dd>
              {dateFormatter.format(startsAt)} ·{' '}
              {timeFormatter.format(startsAt)}
            </dd>
          </div>
          <div>
            <dt>Venue</dt>
            <dd>
              {event.venueName}, {event.city}
            </dd>
          </div>
        </dl>
        <Link className="event-card-link" to={`/events/${event.id}`}>
          View event <span aria-hidden="true">→</span>
        </Link>
      </div>
    </Card>
  );
}
