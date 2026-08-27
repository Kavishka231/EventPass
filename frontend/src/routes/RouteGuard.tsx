import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { Container, Section } from '../components/layout';
import { Loading } from '../components/ui';
import { useSession, type UserRole } from '../features/session';

export function RouteGuard({
  children,
  roles,
}: {
  children: ReactElement;
  roles?: UserRole[];
}) {
  const location = useLocation();
  const { session, status } = useSession();

  if (status === 'loading') {
    return (
      <main className="route-loading" aria-label="Loading account">
        <Container size="small">
          <Section>
            <Loading label="Loading your EventPass account" />
          </Section>
        </Container>
      </main>
    );
  }

  if (status !== 'authenticated' || !session) {
    const returnTo = `${location.pathname}${location.search}`;
    return (
      <Navigate
        to={`/login?returnTo=${encodeURIComponent(returnTo)}`}
        replace
      />
    );
  }

  if (roles && !roles.includes(session.role)) {
    return (
      <Navigate
        to="/unauthorized"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  return children;
}
