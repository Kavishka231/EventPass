import { Outlet } from 'react-router-dom';

import { Container } from '../components/layout';
import { Badge } from '../components/ui';
import {
  Brand,
  MobileNavigation,
  NavigationLinks,
  type NavigationItem,
} from '../components/navigation';
import { useSession } from '../features/session';

export interface WorkspaceShellProps {
  label: string;
  navigation: NavigationItem[];
}

export function WorkspaceShell({ label, navigation }: WorkspaceShellProps) {
  const { session } = useSession();

  return (
    <div className="application-shell workspace-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="workspace-header">
        <Container>
          <div className="header-row">
            <div className="workspace-brand">
              <Brand />
              <span aria-hidden="true" className="workspace-divider" />
              <span className="workspace-label">{label}</span>
            </div>
            <nav
              className="desktop-navigation"
              aria-label={`${label} navigation`}
            >
              <NavigationLinks items={navigation} />
            </nav>
            <div className="workspace-account" aria-label="Current account">
              <span>{session?.displayName}</span>
              {session ? (
                <Badge tone="accent">{session.role.toLowerCase()}</Badge>
              ) : null}
            </div>
            <MobileNavigation items={navigation} />
          </div>
        </Container>
      </header>
      <main id="main-content" className="shell-main" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}
