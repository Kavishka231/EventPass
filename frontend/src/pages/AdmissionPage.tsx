import { useMutation, useQuery } from '@tanstack/react-query';
import { useRef, useState } from 'react';

import { Container, Section } from '../components/layout';
import { AdmissionScanner } from '../components/tickets/AdmissionScanner';
import {
  Alert,
  Button,
  Dialog,
  ErrorState,
  Input,
  Label,
  Loading,
  Select,
  SuccessState,
} from '../components/ui';
import { eventService } from '../features/events';
import { ticketService } from '../features/tickets';
import { ApiError } from '../types';
import type {
  TicketRedemptionResponse,
  TicketValidationResponse,
} from '../types';

const PAGE_SIZE = 100;

function admissionError(error: unknown) {
  if (!(error instanceof ApiError))
    return 'Admission could not be completed. Check the connection and try again.';
  switch (error.code) {
    case 'TICKET_ALREADY_USED':
      return 'This ticket has already been used. Entry cannot be granted again.';
    case 'TICKET_CANCELLED':
      return 'This ticket was cancelled and is not valid for entry.';
    case 'TICKET_NOT_FOUND':
      return 'The ticket code is invalid or no longer exists.';
    case 'TICKET_EVENT_MISMATCH':
      return 'This ticket belongs to a different event.';
    case 'EVENT_NOT_ADMITTING':
      return 'This event is not currently accepting admission.';
    case 'EVENT_ACCESS_DENIED':
      return 'You are not authorized to process admission for this event.';
    default:
      return error.retryable
        ? 'Admission could not be confirmed because the service is unavailable. Try again.'
        : 'Admission could not be completed.';
  }
}

export function AdmissionPage() {
  const validationFlight = useRef(false);
  const redemptionFlight = useRef(false);
  const [eventId, setEventId] = useState('');
  const [manualToken, setManualToken] = useState('');
  const [validated, setValidated] = useState<{
    token: string;
    ticket: TicketValidationResponse;
  } | null>(null);
  const [redeemed, setRedeemed] = useState<TicketRedemptionResponse | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);
  const events = useQuery({
    queryKey: ['admission', 'events'],
    queryFn: () =>
      eventService.list({
        page: 0,
        size: PAGE_SIZE,
        sort: 'startDateTime,asc',
      }),
  });
  const validation = useMutation({
    mutationFn: (token: string) =>
      ticketService.validate({ qrToken: token, eventId }),
    onSuccess: (ticket, token) => {
      validationFlight.current = false;
      setValidated({ ticket, token });
      setManualToken('');
      setError(null);
      setRedeemed(null);
    },
    onError: (failure) => {
      validationFlight.current = false;
      setValidated(null);
      setManualToken('');
      setError(admissionError(failure));
    },
  });
  const redemption = useMutation({
    mutationFn: () => {
      if (!validated) throw new Error('No validated ticket.');
      return ticketService.redeem({ qrToken: validated.token, eventId });
    },
    onSuccess: (ticket) => {
      redemptionFlight.current = false;
      setRedeemed(ticket);
      setValidated(null);
      setError(null);
    },
    onError: (failure) => {
      redemptionFlight.current = false;
      setValidated(null);
      setError(admissionError(failure));
    },
  });

  function validateToken(token: string) {
    const normalized = token.trim();
    if (!eventId) {
      setError('Select the event before validating a ticket.');
      return;
    }
    if (!normalized || validationFlight.current || redemptionFlight.current)
      return;
    validationFlight.current = true;
    validation.mutate(normalized);
  }

  function redeemTicket() {
    if (!validated || redemptionFlight.current) return;
    redemptionFlight.current = true;
    redemption.mutate();
  }

  const busy = validation.isPending || redemption.isPending;

  return (
    <Section className="admission-section">
      <Container>
        <header className="booking-management-heading">
          <p className="discovery-eyebrow">Controlled entry</p>
          <h1>Ticket admission</h1>
          <p>
            Validate each ticket against its event, then explicitly confirm the
            atomic redemption.
          </p>
        </header>

        {events.isPending ? (
          <Loading label="Loading admission events" />
        ) : events.isError ? (
          <ErrorState
            title="Events couldn't be loaded"
            description="Check the connection before processing admission."
            actionLabel="Try again"
            onAction={() => void events.refetch()}
          />
        ) : (
          <div className="admission-layout">
            <div className="admission-workflow">
              <div className="field-group">
                <Label htmlFor="admission-event">Event</Label>
                <Select
                  id="admission-event"
                  value={eventId}
                  disabled={busy}
                  onChange={(event) => {
                    setEventId(event.target.value);
                    setValidated(null);
                    setRedeemed(null);
                    setError(null);
                  }}
                >
                  <option value="">Select an event</option>
                  {events.data?.content.map((event) => (
                    <option key={event.id} value={event.id}>
                      {event.name} —{' '}
                      {new Date(event.startDateTime).toLocaleString()}
                    </option>
                  ))}
                </Select>
              </div>

              <AdmissionScanner
                disabled={!eventId || busy}
                onScan={validateToken}
              />

              <form
                className="admission-manual"
                onSubmit={(event) => {
                  event.preventDefault();
                  validateToken(manualToken);
                }}
              >
                <div>
                  <p className="discovery-eyebrow">Fallback</p>
                  <h2>Enter ticket token manually</h2>
                  <p>Use manual entry when camera access is unavailable.</p>
                </div>
                <div className="field-group">
                  <Label htmlFor="admission-token">Ticket token</Label>
                  <Input
                    id="admission-token"
                    type="password"
                    autoComplete="off"
                    maxLength={128}
                    value={manualToken}
                    disabled={!eventId || busy}
                    onChange={(event) => setManualToken(event.target.value)}
                  />
                </div>
                <Button
                  type="submit"
                  loading={validation.isPending}
                  disabled={!eventId || !manualToken.trim() || busy}
                >
                  Validate ticket
                </Button>
              </form>
            </div>

            <aside className="admission-result" aria-live="polite">
              {error ? (
                <Alert tone="error" title="Admission denied">
                  {error}
                </Alert>
              ) : null}
              {redeemed ? (
                <SuccessState
                  title="Admission confirmed"
                  description={`Ticket ${redeemed.ticketNumber} was redeemed at ${new Date(redeemed.usedAt).toLocaleTimeString()}.`}
                  actionLabel="Scan next ticket"
                  onAction={() => setRedeemed(null)}
                />
              ) : !error ? (
                <div className="admission-guidance">
                  <span aria-hidden="true">1</span>
                  <h2>Ready to validate</h2>
                  <p>
                    Select the relevant event, then scan or manually enter a
                    ticket token.
                  </p>
                </div>
              ) : null}
            </aside>
          </div>
        )}

        <Dialog
          open={Boolean(validated)}
          title="Confirm ticket redemption"
          description="Validation is only a preview. EventPass will recheck and atomically redeem the ticket when you confirm."
          onClose={() => !redemption.isPending && setValidated(null)}
          footer={
            <>
              <Button
                variant="ghost"
                disabled={redemption.isPending}
                onClick={() => setValidated(null)}
              >
                Cancel
              </Button>
              <Button
                loading={redemption.isPending}
                loadingLabel="Redeeming ticket"
                onClick={redeemTicket}
              >
                Confirm admission
              </Button>
            </>
          }
        >
          {validated ? (
            <dl className="admission-ticket-summary">
              <div>
                <dt>Ticket</dt>
                <dd>{validated.ticket.ticketNumber}</dd>
              </div>
              <div>
                <dt>Event</dt>
                <dd>{validated.ticket.eventName}</dd>
              </div>
              <div>
                <dt>Starts</dt>
                <dd>
                  {new Date(
                    validated.ticket.eventStartDateTime,
                  ).toLocaleString()}
                </dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{validated.ticket.status}</dd>
              </div>
            </dl>
          ) : null}
        </Dialog>
      </Container>
    </Section>
  );
}
