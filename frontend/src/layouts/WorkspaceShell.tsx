import { Outlet, useNavigate } from 'react-router-dom';

import { Container } from '../components/layout';
import { Badge, Button } from '../components/ui';
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
  const navigate = useNavigate();
  const sessionController = useSession();
  const { session } = sessionController;

  async function signOut() {
    await sessionController.logout();
    await navigate('/login', { replace: true });
  }

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
              {session ? (
                <Badge tone="accent">{session.role.toLowerCase()}</Badge>
              ) : null}
              <Button
                size="small"
                variant="ghost"
                onClick={() => void signOut()}
              >
                Log out
              </Button>
            </div>
            <MobileNavigation
              items={navigation}
              sessionAction={{ label: 'Log out', onAction: signOut }}
            />
          </div>
        </Container>
      </header>
      <main id="main-content" className="shell-main" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}
