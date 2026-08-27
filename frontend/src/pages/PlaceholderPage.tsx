import { Container, Section, Stack } from '../components/layout';
import { Badge, Panel } from '../components/ui';

export function PlaceholderPage({
  description,
  group,
  title,
}: {
  description: string;
  group: string;
  title: string;
}) {
  return (
    <Section>
      <Container size="large">
        <Panel className="placeholder-page">
          <Stack gap="4">
            <Badge tone="accent">{group}</Badge>
            <h1>{title}</h1>
            <p>{description}</p>
          </Stack>
        </Panel>
      </Container>
    </Section>
  );
}
