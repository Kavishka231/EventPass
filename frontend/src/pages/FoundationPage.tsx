import { useState } from 'react';

import { Container, Grid, Section, Stack } from '../components/layout';
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  Dialog,
  EmptyState,
  FieldError,
  Input,
  Label,
  Loading,
  Panel,
  Radio,
  Select,
  Skeleton,
  Textarea,
} from '../components/ui';

export function FoundationPage() {
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <main className="design-preview">
      <Section>
        <Container>
          <Stack gap="12">
            <header className="design-preview-header">
              <Badge tone="accent">Design foundation</Badge>
              <h1>EventPass interface system</h1>
              <p>
                Reusable tokens and accessible primitives for a refined,
                event-focused product. Feature experiences will be introduced in
                later commits.
              </p>
            </header>

            <Panel>
              <Stack gap="6">
                <div>
                  <p className="text-label design-preview-kicker">Typography</p>
                  <p className="text-display design-preview-display">
                    Live moments, simply booked.
                  </p>
                </div>
                <Grid columns={3}>
                  <Card>
                    <h3>Clear hierarchy</h3>
                    <p className="text-body-small">
                      Manrope gives product information a confident editorial
                      voice.
                    </p>
                  </Card>
                  <Card>
                    <h3>Quiet surfaces</h3>
                    <p className="text-body-small">
                      Warm neutrals and restrained elevation keep events in
                      focus.
                    </p>
                  </Card>
                  <Card>
                    <h3>Purposeful states</h3>
                    <p className="text-body-small">
                      Status, focus, and feedback remain legible without visual
                      noise.
                    </p>
                  </Card>
                </Grid>
              </Stack>
            </Panel>

            <section aria-labelledby="buttons-title">
              <Stack gap="4">
                <h2 id="buttons-title">Actions</h2>
                <div className="design-preview-row">
                  <Button>Primary action</Button>
                  <Button variant="secondary">Secondary</Button>
                  <Button variant="outline">Outline</Button>
                  <Button variant="ghost">Ghost</Button>
                  <Button variant="danger">Danger</Button>
                  <Button loading loadingLabel="Processing" />
                  <Button disabled>Disabled</Button>
                  <Button variant="outline" onClick={() => setDialogOpen(true)}>
                    Open dialog
                  </Button>
                </div>
              </Stack>
            </section>

            <Grid columns={2}>
              <Panel>
                <Stack gap="5">
                  <div>
                    <h2>Form controls</h2>
                    <p className="design-preview-muted">
                      Labels, validation, focus, and disabled states are shared.
                    </p>
                  </div>
                  <div>
                    <Label htmlFor="preview-name">Event name</Label>
                    <Input
                      id="preview-name"
                      placeholder="Summer chamber concert"
                    />
                  </div>
                  <div>
                    <Label htmlFor="preview-category">Category</Label>
                    <Select id="preview-category" defaultValue="">
                      <option value="" disabled>
                        Choose a category
                      </option>
                      <option value="music">Music</option>
                      <option value="theatre">Theatre</option>
                    </Select>
                  </div>
                  <div>
                    <Label htmlFor="preview-notes">Notes</Label>
                    <Textarea
                      id="preview-notes"
                      placeholder="Add helpful event information"
                    />
                  </div>
                  <div>
                    <Label htmlFor="preview-error">Contact email</Label>
                    <Input
                      id="preview-error"
                      aria-invalid="true"
                      aria-describedby="preview-error-message"
                    />
                    <FieldError id="preview-error-message">
                      Enter a valid email address.
                    </FieldError>
                  </div>
                  <div className="design-preview-row">
                    <Checkbox label="Send booking updates" defaultChecked />
                    <Radio
                      name="preview-access"
                      label="Standard admission"
                      defaultChecked
                    />
                    <Radio name="preview-access" label="Priority admission" />
                  </div>
                </Stack>
              </Panel>

              <Panel>
                <Stack gap="5">
                  <div>
                    <h2>Feedback</h2>
                    <p className="design-preview-muted">
                      Meaning and text accompany every status color.
                    </p>
                  </div>
                  <Alert title="Inventory updated" tone="success">
                    Seat availability now reflects the latest saved
                    configuration.
                  </Alert>
                  <Alert title="Review required" tone="warning">
                    Confirm event timing before publication.
                  </Alert>
                  <Alert title="Unable to continue" tone="error">
                    Resolve the validation errors and try again.
                  </Alert>
                  <Loading label="Loading availability" />
                  <Stack gap="2">
                    <Skeleton style={{ width: '65%' }} />
                    <Skeleton style={{ width: '90%' }} />
                  </Stack>
                  <EmptyState
                    title="Nothing here yet"
                    description="Content will appear here when it becomes available."
                  />
                </Stack>
              </Panel>
            </Grid>
          </Stack>
        </Container>
      </Section>

      <Dialog
        open={dialogOpen}
        title="Dialog foundation"
        description="Native dialog behavior provides focus management and keyboard dismissal."
        onClose={() => setDialogOpen(false)}
        footer={
          <Button variant="secondary" onClick={() => setDialogOpen(false)}>
            Close
          </Button>
        }
      >
        <p>
          Feature-specific confirmation and form content will use this shared
          surface.
        </p>
      </Dialog>
    </main>
  );
}
