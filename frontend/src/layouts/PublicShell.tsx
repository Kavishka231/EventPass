import { Link, Outlet } from 'react-router-dom';

import { Container } from '../components/layout';
import {
  Brand,
  MobileNavigation,
  NavigationLinks,
  type NavigationItem,
} from '../components/navigation';
import { useSession } from '../features/session';

const publicNavigation: NavigationItem[] = [
  { label: 'Home', to: '/', end: true },
  { label: 'Events', to: '/events' },
];

function accountDestination(role: string) {
  if (role === 'ADMIN') return '/admin';
  if (role === 'ORGANIZER') return '/organizer';
  return '/bookings';
}

export function PublicShell() {
  const { session, status } = useSession();
  const authenticated = status === 'authenticated' && session;
  const primaryAction = authenticated
    ? { label: 'Open account', to: accountDestination(session.role) }
    : { label: 'Get started', to: '/register' };
  const secondaryAction = authenticated
    ? undefined
    : { label: 'Log in', to: '/login' };

  return (
    <div className="application-shell public-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="public-header">
        <Container>
          <div className="header-row">
            <Brand />
            <nav className="desktop-navigation" aria-label="Primary navigation">
              <NavigationLinks items={publicNavigation} />
            </nav>
            <div className="desktop-header-actions">
              {secondaryAction ? (
                <Link
                  className="button link-button"
                  data-size="medium"
                  data-variant="ghost"
                  to={secondaryAction.to}
                >
                  {secondaryAction.label}
                </Link>
              ) : null}
              <Link
                className="button link-button"
                data-size="medium"
                data-variant="primary"
                to={primaryAction.to}
              >
                {primaryAction.label}
              </Link>
            </div>
            <MobileNavigation
              items={publicNavigation}
              primaryAction={primaryAction}
              secondaryAction={secondaryAction}
            />
          </div>
        </Container>
      </header>

      <main id="main-content" className="shell-main" tabIndex={-1}>
        <Outlet />
      </main>

      <footer className="public-footer">
        <Container>
          <div className="footer-grid">
            <div>
              <Brand />
              <p>Confident event discovery and secure digital ticketing.</p>
            </div>
            <nav aria-label="Footer navigation">
              <Link to="/events">Browse events</Link>
              <Link to="/login">Log in</Link>
              <Link to="/register">Create account</Link>
            </nav>
          </div>
          <p className="footer-legal">
            © {new Date().getFullYear()} EventPass. All rights reserved.
          </p>
        </Container>
      </footer>
    </div>
  );
}
