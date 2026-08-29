import { Card, Skeleton } from '../ui';

export function EventCardSkeleton() {
  return (
    <Card className="event-card event-card-skeleton" aria-hidden="true">
      <Skeleton className="event-card-visual" />
      <div className="event-card-content">
        <Skeleton className="skeleton-label" />
        <Skeleton className="skeleton-title" />
        <Skeleton />
        <Skeleton />
      </div>
    </Card>
  );
}
