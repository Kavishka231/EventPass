import type { NavigationItem } from '../components/navigation';
import { useUnreadNotificationCount } from '../features/notifications';
import { WorkspaceShell } from './WorkspaceShell';

function countBadge(count: number) {
  return {
    text: count > 99 ? '99+' : String(count),
    accessibleLabel: `${count} unread ${count === 1 ? 'notification' : 'notifications'}`,
  };
}

export function CustomerShell() {
  const unread = useUnreadNotificationCount();
  const count = unread.data?.unreadCount ?? 0;
  const navigation: NavigationItem[] = [
    { label: 'Bookings', to: '/bookings' },
    { label: 'Tickets', to: '/tickets' },
    {
      label: 'Notifications',
      to: '/notifications',
      badge: count > 0 ? countBadge(count) : undefined,
    },
  ];

  return <WorkspaceShell label="My EventPass" navigation={navigation} />;
}
