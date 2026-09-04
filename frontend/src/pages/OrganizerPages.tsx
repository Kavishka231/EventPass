import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react';
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { Container, Grid, Section, Stack } from '../components/layout';
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  EmptyState,
  ErrorState,
  Input,
  Label,
  Select,
  Skeleton,
  Textarea,
} from '../components/ui';
import {
  useCancelEvent,
  useConfigureInventory,
  useEventMutation,
  useOrganizerBookings,
  useOrganizerEvent,
  useOrganizerEvents,
  useOrganizerInventory,
  useOrganizerVenues,
} from '../features/organizer';
import type {
  EventRequest,
  EventResponse,
  OrganizerInventoryOption,
} from '../types';

const PAGE_SIZE = 12;
const pageFrom = (value: string | null) => {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 1;
};
const badgeTone = (status: EventResponse['status']) =>
  status === 'PUBLISHED'
    ? 'success'
    : status === 'CANCELLED'
      ? 'error'
      : status === 'DRAFT'
        ? 'warning'
        : 'neutral';
const dateTimeLocal = (value: string) => {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
};

function Header({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="organizer-heading">
      <div>
        <p className="discovery-eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </header>
  );
}
function Pager({
  current,
  total,
  pending,
  onChange,
}: {
  current: number;
  total: number;
  pending: boolean;
  onChange: (page: number) => void;
}) {
  if (total <= 1) return null;
  return (
    <nav className="pagination" aria-label="Results pages">
      <Button
        variant="outline"
        disabled={current === 1 || pending}
        onClick={() => onChange(current - 1)}
      >
        Previous
      </Button>
      <span>
        Page {current} of {total}
      </span>
      <Button
        variant="outline"
        disabled={current === total || pending}
        onClick={() => onChange(current + 1)}
      >
        Next
      </Button>
    </nav>
  );
}
function EventCard({ event }: { event: EventResponse }) {
  return (
    <Card className="organizer-event-card">
      <div className="organizer-card-title">
        <div>
          <p className="discovery-eyebrow">{event.category}</p>
          <h2>{event.name}</h2>
        </div>
        <Badge tone={badgeTone(event.status)}>{event.status}</Badge>
      </div>
      <p>
        {new Date(event.startDateTime).toLocaleString()} · {event.venueName},{' '}
        {event.city}
      </p>
      <div className="organizer-actions">
        <Link
          className="button"
          data-variant="outline"
          to={`/organizer/events/${event.id}/edit`}
        >
          Edit
        </Link>
        <Link
          className="button"
          data-variant="outline"
          to={`/organizer/events/${event.id}/inventory`}
        >
          Inventory
        </Link>
        <Link
          className="button"
          data-variant="ghost"
          to={`/organizer/events/${event.id}/bookings`}
        >
          Bookings
        </Link>
      </div>
    </Card>
  );
}
function EventCollection({ dashboard = false }: { dashboard?: boolean }) {
  const [params, setParams] = useSearchParams();
  const page = dashboard ? 1 : pageFrom(params.get('page'));
  const events = useOrganizerEvents({
    page: page - 1,
    size: dashboard ? 6 : PAGE_SIZE,
    sort: 'startDateTime,desc',
  });
  if (events.isPending)
    return (
      <Grid>
        {[1, 2, 3].map((key) => (
          <Skeleton className="organizer-card-skeleton" key={key} />
        ))}
      </Grid>
    );
  if (events.isError)
    return (
      <ErrorState
        title="Events couldn't be loaded"
        description="Check your connection and try again."
        actionLabel="Try again"
        onAction={() => void events.refetch()}
      />
    );
  if (!events.data.content.length)
    return (
      <EmptyState
        title="No events yet"
        description="Create your first draft event to begin configuring inventory."
      />
    );
  return (
    <>
      <Grid className={events.isFetching ? 'results-updating' : ''}>
        {events.data.content.map((event) => (
          <EventCard event={event} key={event.id} />
        ))}
      </Grid>
      {!dashboard ? (
        <Pager
          current={page}
          total={events.data.totalPages}
          pending={events.isFetching}
          onChange={(next) => {
            const copy = new URLSearchParams(params);
            if (next === 1) copy.delete('page');
            else copy.set('page', String(next));
            setParams(copy);
          }}
        />
      ) : null}
    </>
  );
}

export function OrganizerDashboardPage() {
  return (
    <Section>
      <Container>
        <Header
          eyebrow="Organizer"
          title="Workspace overview"
          description="Manage your events, inventory, and attendee bookings."
          action={
            <Link className="button" to="/organizer/events/new">
              Create event
            </Link>
          }
        />
        <EventCollection dashboard />
      </Container>
    </Section>
  );
}
export function OrganizerEventsPage() {
  return (
    <Section>
      <Container>
        <Header
          eyebrow="Organizer"
          title="My events"
          description="Draft, publish, and manage only the events owned by your account."
          action={
            <Link className="button" to="/organizer/events/new">
              Create event
            </Link>
          }
        />
        <EventCollection />
      </Container>
    </Section>
  );
}

function EventForm({ event }: { event?: EventResponse }) {
  const navigate = useNavigate();
  const venues = useOrganizerVenues();
  const mutation = useEventMutation(event?.id);
  const [form, setForm] = useState<EventRequest>(() => ({
    name: event?.name ?? '',
    description: event?.description ?? '',
    category: event?.category ?? '',
    startDateTime: event ? dateTimeLocal(event.startDateTime) : '',
    endDateTime: event ? dateTimeLocal(event.endDateTime) : '',
    venueId: event?.venueId ?? '',
    status: event?.status ?? 'DRAFT',
  }));
  const locked = event?.status === 'CANCELLED' || event?.status === 'COMPLETED';
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const saved = await mutation.mutateAsync({
      ...form,
      startDateTime: new Date(form.startDateTime).toISOString(),
      endDateTime: new Date(form.endDateTime).toISOString(),
    });
    await navigate(`/organizer/events/${saved.id}/edit`, { replace: !event });
  };
  if (venues.isPending) return <Skeleton className="organizer-form-skeleton" />;
  if (venues.isError)
    return (
      <ErrorState
        title="Venues couldn't be loaded"
        description="Venue options are required to save an event."
        actionLabel="Try again"
        onAction={() => void venues.refetch()}
      />
    );
  if (!venues.data.content.length)
    return (
      <EmptyState
        title="No venues available"
        description="An administrator must create a venue and its seats before organizers can create events."
      />
    );
  return (
    <form className="panel organizer-form" onSubmit={(e) => void submit(e)}>
      <fieldset disabled={locked || mutation.isPending}>
        <div className="organizer-form-grid">
          <Label>
            Event name
            <Input
              required
              maxLength={250}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </Label>
          <Label>
            Category
            <Input
              required
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value })}
            />
          </Label>
          <Label>
            Starts
            <Input
              required
              type="datetime-local"
              value={form.startDateTime}
              onChange={(e) =>
                setForm({ ...form, startDateTime: e.target.value })
              }
            />
          </Label>
          <Label>
            Ends
            <Input
              required
              type="datetime-local"
              value={form.endDateTime}
              onChange={(e) =>
                setForm({ ...form, endDateTime: e.target.value })
              }
            />
          </Label>
          <Label>
            Venue
            <Select
              required
              value={form.venueId}
              onChange={(e) => setForm({ ...form, venueId: e.target.value })}
            >
              <option value="">Select venue</option>
              {venues.data.content.map((venue) => (
                <option value={venue.id} key={venue.id}>
                  {venue.name} · {venue.city}
                </option>
              ))}
            </Select>
          </Label>
          <Label>
            Status
            <Select
              value={form.status}
              onChange={(e) =>
                setForm({
                  ...form,
                  status: e.target.value as EventRequest['status'],
                })
              }
            >
              <option value="DRAFT">Draft</option>
              {event ? <option value="PUBLISHED">Published</option> : null}
            </Select>
          </Label>
          <Label className="organizer-full-field">
            Description
            <Textarea
              required
              maxLength={4000}
              rows={6}
              value={form.description}
              onChange={(e) =>
                setForm({ ...form, description: e.target.value })
              }
            />
          </Label>
        </div>
      </fieldset>
      {mutation.isError ? (
        <Alert title="Event couldn't be saved" tone="error">
          Review the details and inventory requirements, then try again.
        </Alert>
      ) : null}
      <div className="organizer-actions">
        <Button type="submit" loading={mutation.isPending} disabled={locked}>
          {event ? 'Save event' : 'Create draft'}
        </Button>
        <Button
          variant="ghost"
          onClick={() => void navigate('/organizer/events')}
        >
          Back
        </Button>
      </div>
    </form>
  );
}
export function OrganizerEventFormPage() {
  const id = useParams().eventId;
  const event = useOrganizerEvent(id ?? '');
  const cancel = useCancelEvent();
  const navigate = useNavigate();
  if (id && event.isPending)
    return (
      <Section>
        <Container>
          <Skeleton className="organizer-form-skeleton" />
        </Container>
      </Section>
    );
  if (id && event.isError)
    return (
      <Section>
        <Container>
          <ErrorState
            title="Event couldn't be loaded"
            description="It may not exist or belong to your account."
            actionLabel="Back to events"
            onAction={() => void navigate('/organizer/events')}
          />
        </Container>
      </Section>
    );
  const current = event.data;
  return (
    <Section>
      <Container size="large">
        <Header
          eyebrow="Event management"
          title={current ? `Edit ${current.name}` : 'Create event'}
          description={
            current
              ? 'Update event details or move a configured draft to published.'
              : 'New events begin as drafts so inventory can be configured safely.'
          }
        />
        {current?.status === 'CANCELLED' ? (
          <Alert title="Event cancelled" tone="warning">
            Cancelled events are read-only.
          </Alert>
        ) : null}
        <EventForm event={current} />
        {current &&
        current.status !== 'CANCELLED' &&
        current.status !== 'COMPLETED' ? (
          <div className="danger-zone">
            <h2>Cancel event</h2>
            <p>
              This is a permanent transition and may start refunds for confirmed
              bookings.
            </p>
            {cancel.isError ? (
              <Alert title="Cancellation couldn't be completed" tone="error">
                Wait for any pending booking payment to finish, then retry.
              </Alert>
            ) : null}
            <Button
              variant="danger"
              loading={cancel.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    `Cancel ${current.name}? This cannot be undone.`,
                  )
                )
                  cancel.mutate(current.id, {
                    onSuccess: () => void event.refetch(),
                  });
              }}
            >
              Cancel event
            </Button>
          </div>
        ) : null}
      </Container>
    </Section>
  );
}

function InventoryEditor({
  event,
  seats,
}: {
  event: EventResponse;
  seats: OrganizerInventoryOption[];
}) {
  const mutation = useConfigureInventory(event.id);
  const [rows, setRows] = useState(() =>
    seats.map((seat) => ({ ...seat, price: seat.price ?? 0 })),
  );
  useEffect(
    () => setRows(seats.map((seat) => ({ ...seat, price: seat.price ?? 0 }))),
    [seats],
  );
  const configured = useMemo(
    () => rows.filter((row) => row.configured),
    [rows],
  );
  if (event.status !== 'DRAFT')
    return (
      <Alert title="Inventory locked" tone="warning">
        Inventory can only be changed while an event is a draft.
      </Alert>
    );
  return (
    <Stack>
      <div className="inventory-toolbar">
        <p>
          {configured.length} of {rows.length} seats configured
        </p>
        <Button
          variant="outline"
          onClick={() =>
            setRows(rows.map((row) => ({ ...row, configured: true })))
          }
        >
          Select all
        </Button>
      </div>
      <div className="inventory-table-wrap">
        <table className="organizer-table">
          <thead>
            <tr>
              <th>Use</th>
              <th>Seat</th>
              <th>Type</th>
              <th>Price (LKR)</th>
              <th>Blocked</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={row.seatId}>
                <td>
                  <Checkbox
                    aria-label={`Configure ${row.section} ${row.row}-${row.number}`}
                    checked={row.configured}
                    onChange={(e) =>
                      setRows(
                        rows.map((item, i) =>
                          i === index
                            ? { ...item, configured: e.target.checked }
                            : item,
                        ),
                      )
                    }
                    label=""
                  />
                </td>
                <td>
                  {row.section} · {row.row}-{row.number}
                </td>
                <td>{row.type}</td>
                <td>
                  <Input
                    aria-label={`Price for ${row.section} ${row.row}-${row.number}`}
                    type="number"
                    min="0"
                    step="0.01"
                    value={row.price}
                    disabled={!row.configured}
                    onChange={(e) =>
                      setRows(
                        rows.map((item, i) =>
                          i === index
                            ? { ...item, price: Number(e.target.value) }
                            : item,
                        ),
                      )
                    }
                  />
                </td>
                <td>
                  <Checkbox
                    checked={row.blocked}
                    disabled={!row.configured}
                    onChange={(e) =>
                      setRows(
                        rows.map((item, i) =>
                          i === index
                            ? { ...item, blocked: e.target.checked }
                            : item,
                        ),
                      )
                    }
                    label="Block"
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {mutation.isError ? (
        <Alert title="Inventory couldn't be saved" tone="error">
          Check prices and try again.
        </Alert>
      ) : null}
      <Button
        loading={mutation.isPending}
        disabled={!configured.length}
        onClick={() =>
          mutation.mutate(
            configured.map((row) => ({
              seatId: row.seatId,
              price: row.price,
              blocked: row.blocked,
            })),
          )
        }
      >
        Save inventory
      </Button>
    </Stack>
  );
}
export function OrganizerInventoryPage() {
  const id = useParams().eventId ?? '';
  const event = useOrganizerEvent(id);
  const inventory = useOrganizerInventory(id);
  if (event.isPending || inventory.isPending)
    return (
      <Section>
        <Container>
          <Skeleton className="organizer-form-skeleton" />
        </Container>
      </Section>
    );
  if (event.isError || inventory.isError)
    return (
      <Section>
        <Container>
          <ErrorState
            title="Inventory couldn't be loaded"
            description="Retry the owned event and venue seat request."
            actionLabel="Try again"
            onAction={() => {
              void event.refetch();
              void inventory.refetch();
            }}
          />
        </Container>
      </Section>
    );
  return (
    <Section>
      <Container>
        <Header
          eyebrow="Inventory"
          title={event.data.name}
          description="Set authoritative prices and block seats that must not be sold."
        />
        {inventory.data.length ? (
          <InventoryEditor event={event.data} seats={inventory.data} />
        ) : (
          <EmptyState
            title="No venue seats"
            description="An administrator must add physical seats to this venue."
          />
        )}
      </Container>
    </Section>
  );
}

export function OrganizerBookingsPage() {
  const id = useParams().eventId ?? '';
  const [params, setParams] = useSearchParams();
  const page = pageFrom(params.get('page'));
  const event = useOrganizerEvent(id);
  const bookings = useOrganizerBookings(id, {
    page: page - 1,
    size: 20,
    sort: 'createdAt,desc',
  });
  return (
    <Section>
      <Container>
        <Header
          eyebrow="Booking report"
          title={event.data?.name ?? 'Event bookings'}
          description="Review customer bookings and reserved event-seat identifiers."
        />
        {bookings.isPending ? (
          <Skeleton className="organizer-table-skeleton" />
        ) : bookings.isError ? (
          <ErrorState
            title="Bookings couldn't be loaded"
            description="Check access and try again."
            actionLabel="Try again"
            onAction={() => void bookings.refetch()}
          />
        ) : !bookings.data.content.length ? (
          <EmptyState
            title="No bookings yet"
            description="Customer bookings for this event will appear here."
          />
        ) : (
          <div className={bookings.isFetching ? 'results-updating' : ''}>
            <div className="inventory-table-wrap">
              <table className="organizer-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Customer</th>
                    <th>Status</th>
                    <th>Seats</th>
                    <th>Total</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.data.content.map((booking) => (
                    <tr key={booking.id}>
                      <td>{booking.reference}</td>
                      <td>
                        {booking.customerFirstName} {booking.customerLastName}
                        <small>{booking.customerEmail}</small>
                      </td>
                      <td>
                        <Badge>{booking.status}</Badge>
                      </td>
                      <td>{booking.eventSeatIds.length}</td>
                      <td>
                        {new Intl.NumberFormat(undefined, {
                          style: 'currency',
                          currency: booking.currency,
                        }).format(booking.totalAmount)}
                      </td>
                      <td>{new Date(booking.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              current={page}
              total={bookings.data.totalPages}
              pending={bookings.isFetching}
              onChange={(next) => {
                const copy = new URLSearchParams(params);
                if (next === 1) copy.delete('page');
                else copy.set('page', String(next));
                setParams(copy);
              }}
            />
          </div>
        )}
      </Container>
    </Section>
  );
}
