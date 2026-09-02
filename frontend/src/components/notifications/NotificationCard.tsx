import { Button } from '../ui';
import type { NotificationResponse } from '../../types';

const typeLabels: Record<string, string> = {
  BOOKING_CREATED: 'Booking',
  BOOKING_CANCELLED: 'Cancellation',
  BOOKING_EXPIRED: 'Booking',
  PAYMENT_COMPLETED: 'Payment',
  PAYMENT_FAILED: 'Payment',
  PAYMENT_RECONCILIATION_REQUIRED: 'Payment review',
  TICKET_GENERATED: 'Ticket',
};

export function NotificationCard({
  markingRead,
  readActionDisabled,
  notification,
  onMarkRead,
}: {
  markingRead: boolean;
  readActionDisabled: boolean;
  notification: NotificationResponse;
  onMarkRead: (notificationId: string) => void;
}) {
  const unread = notification.readAt === null;
  return (
    <article className="notification-card" data-unread={unread || undefined}>
      <div className="notification-marker" aria-hidden="true" />
      <div className="notification-content">
        <header className="notification-heading">
          <div>
            <p className="notification-type">
              {typeLabels[notification.type] ?? 'EventPass update'}
            </p>
            <h2>{notification.title}</h2>
          </div>
          {unread ? (
            <span className="notification-unread-label">Unread</span>
          ) : (
            <span className="notification-read-label">Read</span>
          )}
        </header>
        <p className="notification-message">{notification.message}</p>
        <footer className="notification-footer">
          <time dateTime={notification.createdAt}>
            {new Date(notification.createdAt).toLocaleString(undefined, {
              dateStyle: 'medium',
              timeStyle: 'short',
            })}
          </time>
          {unread ? (
            <Button
              variant="ghost"
              size="small"
              loading={markingRead}
              disabled={readActionDisabled}
              loadingLabel="Marking as read..."
              aria-label={`Mark “${notification.title}” as read`}
              onClick={() => onMarkRead(notification.id)}
            >
              Mark as read
            </Button>
          ) : notification.readAt ? (
            <span>
              Read{' '}
              <time dateTime={notification.readAt}>
                {new Date(notification.readAt).toLocaleDateString()}
              </time>
            </span>
          ) : null}
        </footer>
      </div>
    </article>
  );
}
