import { useNavigate } from 'react-router-dom';

import { Container, Section } from '../components/layout';
import { ErrorState } from '../components/ui';

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <Section>
      <Container size="small">
        <ErrorState
          title="Page not found"
          description="The page may have moved, or the address may be incorrect."
          actionLabel="Return home"
          onAction={() => {
            void navigate('/');
          }}
        />
      </Container>
    </Section>
  );
}

export function UnauthorizedPage() {
  const navigate = useNavigate();

  return (
    <Section>
      <Container size="small">
        <ErrorState
          title="Access denied"
          description="Your current EventPass role cannot open this area. Backend permissions remain authoritative."
          actionLabel="Return home"
          onAction={() => {
            void navigate('/');
          }}
        />
      </Container>
    </Section>
  );
}
