import { useState, type FormEvent, type ReactNode } from 'react';
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
  EmptyState,
  ErrorState,
  Input,
  Label,
  Select,
  Skeleton,
  Textarea,
} from '../components/ui';
import {
  useAdminBooking,
  useAdminBookings,
  useAdminEvent,
  useAdminEvents,
  useAdminSeats,
  useAdminStatistics,
  useAdminUsers,
  useAdminVenue,
  useAdminVenues,
  useCancelAdminBooking,
  useCancelAdminEvent,
  useCreateSeats,
  useDeleteVenue,
  useUpdateAdminEvent,
  useUpdateUser,
  useVenueMutation,
} from '../features/admin';
import { useSession } from '../features/session';
import type {
  AdminUserResponse,
  CreateSeatRequest,
  EventRequest,
  EventResponse,
  UpdateUserRequest,
  VenueRequest,
  VenueResponse,
} from '../types';

const SIZE = 20;
const currentPage = (value: string | null) => {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : 1;
};
function Header({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="organizer-heading">
      <div>
        <p className="discovery-eyebrow">Administration</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </header>
  );
}
function Pager({
  page,
  total,
  busy,
  setPage,
}: {
  page: number;
  total: number;
  busy: boolean;
  setPage: (n: number) => void;
}) {
  if (total <= 1) return null;
  return (
    <nav className="pagination">
      <Button
        variant="outline"
        disabled={page === 1 || busy}
        onClick={() => setPage(page - 1)}
      >
        Previous
      </Button>
      <span>
        Page {page} of {total}
      </span>
      <Button
        variant="outline"
        disabled={page === total || busy}
        onClick={() => setPage(page + 1)}
      >
        Next
      </Button>
    </nav>
  );
}
function usePage() {
  const [params, setParams] = useSearchParams();
  const page = currentPage(params.get('page'));
  return {
    page,
    setPage: (next: number) => {
      const copy = new URLSearchParams(params);
      if (next === 1) copy.delete('page');
      else copy.set('page', String(next));
      setParams(copy);
    },
  };
}
function Failure({
  retry,
  title = 'Data could not be loaded',
}: {
  retry: () => void;
  title?: string;
}) {
  return (
    <ErrorState
      title={title}
      description="Check your connection and try again."
      actionLabel="Try again"
      onAction={retry}
    />
  );
}

export function AdminDashboardPage() {
  const statistics = useAdminStatistics();
  return (
    <Section>
      <Container>
        <Header
          title="Platform overview"
          description="Current operational totals from EventPass."
        />
        {statistics.isPending ? (
          <Grid>
            {[1, 2, 3, 4].map((x) => (
              <Skeleton className="admin-stat" key={x} />
            ))}
          </Grid>
        ) : statistics.isError ? (
          <Failure retry={() => void statistics.refetch()} />
        ) : (
          <Grid columns={4}>
            {[
              ['Users', statistics.data.users],
              ['Events', statistics.data.events],
              ['Venues', statistics.data.venues],
              ['Bookings', statistics.data.bookings],
            ].map(([label, value]) => (
              <Card className="admin-stat" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
                {label === 'Bookings' ? (
                  <small>{statistics.data.confirmedBookings} confirmed</small>
                ) : null}
              </Card>
            ))}
          </Grid>
        )}
      </Container>
    </Section>
  );
}

function UserRow({ user, self }: { user: AdminUserResponse; self: boolean }) {
  const update = useUpdateUser();
  const [draft, setDraft] = useState<UpdateUserRequest>({
    role: user.role,
    status: user.status,
  });
  const changed = draft.role !== user.role || draft.status !== user.status;
  return (
    <tr>
      <td>
        {user.firstName} {user.lastName}
        <small>{user.email}</small>
        {self ? <Badge tone="info">You</Badge> : null}
      </td>
      <td>
        <Select
          aria-label={`Role for ${user.email}`}
          disabled={self || update.isPending}
          value={draft.role}
          onChange={(e) =>
            setDraft({
              ...draft,
              role: e.target.value as UpdateUserRequest['role'],
            })
          }
        >
          <option>CUSTOMER</option>
          <option>ORGANIZER</option>
          <option>ADMIN</option>
        </Select>
      </td>
      <td>
        <Select
          aria-label={`Status for ${user.email}`}
          disabled={self || update.isPending}
          value={draft.status}
          onChange={(e) =>
            setDraft({
              ...draft,
              status: e.target.value as UpdateUserRequest['status'],
            })
          }
        >
          <option>ACTIVE</option>
          <option>SUSPENDED</option>
          <option>DISABLED</option>
        </Select>
      </td>
      <td>
        <Button
          size="small"
          disabled={!changed || self}
          loading={update.isPending}
          onClick={() => update.mutate({ id: user.id, request: draft })}
        >
          Save
        </Button>
        {update.isError ? (
          <small role="alert">
            Change rejected. Self-change and last-active-admin protections
            remain enforced.
          </small>
        ) : null}
      </td>
    </tr>
  );
}
export function AdminUsersPage() {
  const { session } = useSession();
  const { page, setPage } = usePage();
  const users = useAdminUsers({
    page: page - 1,
    size: SIZE,
    sort: 'createdAt,desc',
  });
  return (
    <Section>
      <Container>
        <Header
          title="User management"
          description="Manage roles and account status without weakening administrator safeguards."
        />
        {users.isPending ? (
          <Skeleton className="organizer-table-skeleton" />
        ) : users.isError ? (
          <Failure
            retry={() => void users.refetch()}
            title="Users couldn't be loaded"
          />
        ) : !users.data.content.length ? (
          <EmptyState
            title="No users"
            description="No user records are available."
          />
        ) : (
          <>
            <div className="inventory-table-wrap">
              <table className="organizer-table">
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.content.map((user) => (
                    <UserRow
                      user={user}
                      self={user.id === session?.userId}
                      key={user.id}
                    />
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              page={page}
              total={users.data.totalPages}
              busy={users.isFetching}
              setPage={setPage}
            />
          </>
        )}
      </Container>
    </Section>
  );
}

function VenueForm({
  venue,
  onSaved,
}: {
  venue?: VenueResponse;
  onSaved?: () => void;
}) {
  const mutation = useVenueMutation(venue?.id);
  const [form, setForm] = useState<VenueRequest>({
    name: venue?.name ?? '',
    address: venue?.address ?? '',
    city: venue?.city ?? '',
    capacity: venue?.capacity ?? 1,
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate(form, { onSuccess: onSaved });
  };
  return (
    <form className="admin-inline-form" onSubmit={submit}>
      <Label>
        Name
        <Input
          required
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
        />
      </Label>
      <Label>
        Address
        <Input
          required
          value={form.address}
          onChange={(e) => setForm({ ...form, address: e.target.value })}
        />
      </Label>
      <Label>
        City
        <Input
          required
          value={form.city}
          onChange={(e) => setForm({ ...form, city: e.target.value })}
        />
      </Label>
      <Label>
        Capacity
        <Input
          required
          min="1"
          type="number"
          value={form.capacity}
          onChange={(e) =>
            setForm({ ...form, capacity: Number(e.target.value) })
          }
        />
      </Label>
      {mutation.isError ? (
        <Alert title="Venue couldn't be saved" tone="error">
          Review the values and try again.
        </Alert>
      ) : null}
      <Button type="submit" loading={mutation.isPending}>
        {venue ? 'Save venue' : 'Create venue'}
      </Button>
    </form>
  );
}
export function AdminVenuesPage() {
  const { page, setPage } = usePage();
  const venues = useAdminVenues({
    page: page - 1,
    size: SIZE,
    sort: 'name,asc',
  });
  return (
    <Section>
      <Container>
        <Header
          title="Venue management"
          description="Create venues and manage their physical seat definitions."
        />
        <Card className="admin-create-card">
          <h2>Create venue</h2>
          <VenueForm />
        </Card>
        {venues.isPending ? (
          <Skeleton className="organizer-table-skeleton" />
        ) : venues.isError ? (
          <Failure retry={() => void venues.refetch()} />
        ) : !venues.data.content.length ? (
          <EmptyState
            title="No venues"
            description="Create the first venue above."
          />
        ) : (
          <>
            <Grid>
              {venues.data.content.map((venue) => (
                <Link
                  className="card admin-venue-card"
                  to={`/admin/venues/${venue.id}`}
                  key={venue.id}
                >
                  <h2>{venue.name}</h2>
                  <p>
                    {venue.address}, {venue.city}
                  </p>
                  <strong>{venue.capacity} seats maximum</strong>
                </Link>
              ))}
            </Grid>
            <Pager
              page={page}
              total={venues.data.totalPages}
              busy={venues.isFetching}
              setPage={setPage}
            />
          </>
        )}
      </Container>
    </Section>
  );
}
function SeatForm({ venueId }: { venueId: string }) {
  const mutation = useCreateSeats(venueId);
  const [seat, setSeat] = useState<CreateSeatRequest>({
    section: '',
    row: '',
    number: '',
    type: 'REGULAR',
  });
  return (
    <form
      className="admin-seat-form"
      onSubmit={(e) => {
        e.preventDefault();
        mutation.mutate([seat], {
          onSuccess: () => setSeat({ ...seat, number: '' }),
        });
      }}
    >
      <Label>
        Section
        <Input
          required
          value={seat.section}
          onChange={(e) => setSeat({ ...seat, section: e.target.value })}
        />
      </Label>
      <Label>
        Row
        <Input
          required
          value={seat.row}
          onChange={(e) => setSeat({ ...seat, row: e.target.value })}
        />
      </Label>
      <Label>
        Seat number
        <Input
          required
          value={seat.number}
          onChange={(e) => setSeat({ ...seat, number: e.target.value })}
        />
      </Label>
      <Label>
        Type
        <Select
          value={seat.type}
          onChange={(e) =>
            setSeat({
              ...seat,
              type: e.target.value as CreateSeatRequest['type'],
            })
          }
        >
          <option>REGULAR</option>
          <option>PREMIUM</option>
          <option>VIP</option>
        </Select>
      </Label>
      <Button type="submit" loading={mutation.isPending}>
        Add seat
      </Button>
      {mutation.isError ? (
        <small role="alert">
          Seat could not be added. Check uniqueness and venue capacity.
        </small>
      ) : null}
    </form>
  );
}
export function AdminVenuePage() {
  const id = useParams().venueId ?? '';
  const navigate = useNavigate();
  const { page, setPage } = usePage();
  const venue = useAdminVenue(id);
  const seats = useAdminSeats(id, {
    page: page - 1,
    size: SIZE,
    sort: ['section,asc', 'rowNumber,asc', 'seatNumber,asc'],
  });
  const remove = useDeleteVenue();
  if (venue.isPending)
    return (
      <Section>
        <Container>
          <Skeleton className="organizer-form-skeleton" />
        </Container>
      </Section>
    );
  if (venue.isError)
    return (
      <Section>
        <Container>
          <Failure retry={() => void venue.refetch()} />
        </Container>
      </Section>
    );
  return (
    <Section>
      <Container>
        <Header
          title={venue.data.name}
          description="Edit venue details and add physical seats."
        />
        <Card>
          <VenueForm venue={venue.data} />
        </Card>
        <Card className="admin-create-card">
          <h2>Add physical seat</h2>
          <SeatForm venueId={id} />
        </Card>
        {seats.isPending ? (
          <Skeleton className="organizer-table-skeleton" />
        ) : seats.isError ? (
          <Failure retry={() => void seats.refetch()} />
        ) : !seats.data.content.length ? (
          <EmptyState
            title="No physical seats"
            description="Add the first seat above."
          />
        ) : (
          <>
            <div className="inventory-table-wrap">
              <table className="organizer-table">
                <thead>
                  <tr>
                    <th>Section</th>
                    <th>Row</th>
                    <th>Number</th>
                    <th>Type</th>
                  </tr>
                </thead>
                <tbody>
                  {seats.data.content.map((seat) => (
                    <tr key={seat.id}>
                      <td>{seat.section}</td>
                      <td>{seat.row}</td>
                      <td>{seat.number}</td>
                      <td>{seat.type}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              page={page}
              total={seats.data.totalPages}
              busy={seats.isFetching}
              setPage={setPage}
            />
          </>
        )}
        <div className="danger-zone">
          <h2>Delete venue</h2>
          {remove.isError ? (
            <Alert title="Venue couldn't be deleted" tone="error">
              It may still be referenced by seats or events.
            </Alert>
          ) : null}
          <Button
            variant="danger"
            loading={remove.isPending}
            onClick={() => {
              if (confirm(`Delete ${venue.data.name}?`))
                remove.mutate(id, {
                  onSuccess: () => void navigate('/admin/venues'),
                });
            }}
          >
            Delete venue
          </Button>
        </div>
      </Container>
    </Section>
  );
}

function EventAdminForm({ event }: { event: EventResponse }) {
  const mutation = useUpdateAdminEvent(event.id);
  const cancel = useCancelAdminEvent();
  const [form, setForm] = useState<EventRequest>({ ...event });
  const locked = event.status === 'CANCELLED' || event.status === 'COMPLETED';
  return (
    <Stack>
      <form
        className="panel organizer-form"
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate(form);
        }}
      >
        <fieldset disabled={locked || mutation.isPending}>
          <div className="organizer-form-grid">
            <Label>
              Name
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </Label>
            <Label>
              Category
              <Input
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
              />
            </Label>
            <Label className="organizer-full-field">
              Description
              <Textarea
                value={form.description}
                onChange={(e) =>
                  setForm({ ...form, description: e.target.value })
                }
              />
            </Label>
          </div>
        </fieldset>
        {mutation.isError ? (
          <Alert title="Event couldn't be updated" tone="error">
            The transition or event values were rejected.
          </Alert>
        ) : null}
        <Button type="submit" disabled={locked} loading={mutation.isPending}>
          Save event
        </Button>
      </form>
      {!locked ? (
        <div className="danger-zone">
          <h2>Cancel event</h2>
          {cancel.isError ? (
            <Alert title="Cancellation failed" tone="error">
              Pending payments may need to finish before retrying.
            </Alert>
          ) : null}
          <Button
            variant="danger"
            loading={cancel.isPending}
            onClick={() => {
              if (confirm(`Cancel ${event.name}?`)) cancel.mutate(event.id);
            }}
          >
            Cancel event
          </Button>
        </div>
      ) : null}
    </Stack>
  );
}
export function AdminEventsPage() {
  const { page, setPage } = usePage();
  const events = useAdminEvents({
    page: page - 1,
    size: SIZE,
    sort: 'startDateTime,desc',
  });
  return (
    <Section>
      <Container>
        <Header
          title="Event administration"
          description="Review all event states and open an event for controlled updates or cancellation."
        />
        {events.isPending ? (
          <Skeleton className="organizer-table-skeleton" />
        ) : events.isError ? (
          <Failure retry={() => void events.refetch()} />
        ) : !events.data.content.length ? (
          <EmptyState
            title="No events"
            description="No platform events are available."
          />
        ) : (
          <>
            <div className="inventory-table-wrap">
              <table className="organizer-table">
                <thead>
                  <tr>
                    <th>Event</th>
                    <th>Venue</th>
                    <th>Status</th>
                    <th>Start</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {events.data.content.map((event) => (
                    <tr key={event.id}>
                      <td>
                        {event.name}
                        <small>{event.category}</small>
                      </td>
                      <td>{event.venueName}</td>
                      <td>
                        <Badge>{event.status}</Badge>
                      </td>
                      <td>{new Date(event.startDateTime).toLocaleString()}</td>
                      <td>
                        <Link to={`/admin/events/${event.id}`}>Manage</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              page={page}
              total={events.data.totalPages}
              busy={events.isFetching}
              setPage={setPage}
            />
          </>
        )}
      </Container>
    </Section>
  );
}
export function AdminEventPage() {
  const id = useParams().eventId ?? '';
  const event = useAdminEvent(id);
  return (
    <Section>
      <Container>
        {event.isPending ? (
          <Skeleton className="organizer-form-skeleton" />
        ) : event.isError ? (
          <Failure retry={() => void event.refetch()} />
        ) : (
          <>
            <Header
              title={event.data.name}
              description={`${event.data.venueName}, ${event.data.city}`}
            />
            <EventAdminForm event={event.data} />
          </>
        )}
      </Container>
    </Section>
  );
}

export function AdminBookingsPage() {
  const { page, setPage } = usePage();
  const bookings = useAdminBookings({
    page: page - 1,
    size: SIZE,
    sort: 'createdAt,desc',
  });
  return (
    <Section>
      <Container>
        <Header
          title="Booking administration"
          description="Review platform bookings and open eligible records for cancellation."
        />
        {bookings.isPending ? (
          <Skeleton className="organizer-table-skeleton" />
        ) : bookings.isError ? (
          <Failure retry={() => void bookings.refetch()} />
        ) : !bookings.data.content.length ? (
          <EmptyState
            title="No bookings"
            description="No platform bookings are available."
          />
        ) : (
          <>
            <div className="inventory-table-wrap">
              <table className="organizer-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Event</th>
                    <th>Customer</th>
                    <th>Status</th>
                    <th>Total</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.data.content.map((b) => (
                    <tr key={b.id}>
                      <td>{b.reference}</td>
                      <td>{b.eventName}</td>
                      <td>{b.customerEmail}</td>
                      <td>
                        <Badge>{b.status}</Badge>
                      </td>
                      <td>
                        {new Intl.NumberFormat(undefined, {
                          style: 'currency',
                          currency: b.currency,
                        }).format(b.totalAmount)}
                      </td>
                      <td>
                        <Link to={`/admin/bookings/${b.id}`}>View</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              page={page}
              total={bookings.data.totalPages}
              busy={bookings.isFetching}
              setPage={setPage}
            />
          </>
        )}
      </Container>
    </Section>
  );
}
export function AdminBookingPage() {
  const id = useParams().bookingId ?? '';
  const booking = useAdminBooking(id);
  const cancel = useCancelAdminBooking();
  if (booking.isPending)
    return (
      <Section>
        <Container>
          <Skeleton className="organizer-form-skeleton" />
        </Container>
      </Section>
    );
  if (booking.isError)
    return (
      <Section>
        <Container>
          <Failure retry={() => void booking.refetch()} />
        </Container>
      </Section>
    );
  const b = booking.data;
  return (
    <Section>
      <Container size="medium">
        <Header title={b.reference} description={b.eventName} />
        <Card className="admin-booking-detail">
          <dl>
            <div>
              <dt>Customer</dt>
              <dd>{b.customerEmail}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <Badge>{b.status}</Badge>
              </dd>
            </div>
            <div>
              <dt>Seats</dt>
              <dd>{b.eventSeatIds.length}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd>
                {new Intl.NumberFormat(undefined, {
                  style: 'currency',
                  currency: b.currency,
                }).format(b.totalAmount)}
              </dd>
            </div>
          </dl>
        </Card>
        {b.status === 'CONFIRMED' ? (
          <div className="danger-zone">
            <h2>Cancel booking</h2>
            <p>
              The existing refund eligibility and concurrency rules remain
              authoritative.
            </p>
            {cancel.isError ? (
              <Alert title="Cancellation rejected" tone="error">
                The booking may be ineligible or its refund may need
                reconciliation.
              </Alert>
            ) : null}
            <Button
              variant="danger"
              loading={cancel.isPending}
              onClick={() => {
                if (confirm(`Cancel booking ${b.reference}?`))
                  cancel.mutate(b.id, {
                    onSuccess: () => void booking.refetch(),
                  });
              }}
            >
              Cancel booking
            </Button>
          </div>
        ) : null}
      </Container>
    </Section>
  );
}
