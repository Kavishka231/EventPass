import { useRef } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Container, Section } from '../components/layout';
import { NotificationCard } from '../components/notifications';
import {
  Alert,
  Button,
  EmptyState,
  ErrorState,
  Skeleton,
} from '../components/ui';
import {
  useMarkNotificationRead,
  useNotifications,
} from '../features/notifications';

const PAGE_SIZE = 20;

function requestedPage(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

export function NotificationsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentPage = requestedPage(searchParams.get('page'));
  const notifications = useNotifications(currentPage - 1, PAGE_SIZE);
  const markRead = useMarkNotificationRead();
  const submitting = useRef<string | null>(null);

  function selectPage(page: number) {
    const next = new URLSearchParams(searchParams);
    if (page <= 1) next.delete('page');
    else next.set('page', String(page));
    setSearchParams(next);
    document.querySelector('#notification-list')?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  }

  async function markNotificationRead(notificationId: string) {
    if (submitting.current !== null || markRead.isPending) return;
    submitting.current = notificationId;
    try {
      await markRead.mutateAsync(notificationId);
    } catch {
      // The normalized mutation failure is presented without changing read state.
    } finally {
      submitting.current = null;
    }
  }

  return (
    <Section className="notification-section">
      <Container size="large">
        <header className="booking-management-heading" id="notification-list">
          <p className="discovery-eyebrow">Your activity</p>
          <h1>Notifications</h1>
          <p>
            Booking, payment, cancellation, and ticket updates from EventPass.
          </p>
        </header>

        {markRead.isError ? (
          <Alert title="Notification wasn't marked as read" tone="error">
            Your notification remains unread. Check your connection and try
            again.
          </Alert>
        ) : null}

        {notifications.isPending ? (
          <div className="notification-list" aria-label="Loading notifications">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton className="notification-skeleton" key={index} />
            ))}
          </div>
        ) : notifications.isError ? (
          <ErrorState
            title="Notifications couldn't be loaded"
            description="Check your connection and try again."
            actionLabel="Try again"
            onAction={() => void notifications.refetch()}
          />
        ) : notifications.data.content.length === 0 ? (
          <EmptyState
            title="No notifications yet"
            description="Important EventPass updates will appear here."
          />
        ) : (
          <div
            className={`notification-list${notifications.isFetching ? ' results-updating' : ''}`}
          >
            {notifications.data.content.map((notification) => (
              <NotificationCard
                key={notification.id}
                notification={notification}
                markingRead={
                  markRead.isPending && markRead.variables === notification.id
                }
                readActionDisabled={markRead.isPending}
                onMarkRead={(notificationId) =>
                  void markNotificationRead(notificationId)
                }
              />
            ))}
          </div>
        )}

        {notifications.data && notifications.data.totalPages > 1 ? (
          <nav className="pagination" aria-label="Notification pages">
            <Button
              variant="outline"
              disabled={notifications.data.first || notifications.isFetching}
              onClick={() => selectPage(currentPage - 1)}
            >
              Previous
            </Button>
            <span aria-live="polite">
              Page {currentPage} of {notifications.data.totalPages}
            </span>
            <Button
              variant="outline"
              disabled={notifications.data.last || notifications.isFetching}
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
