# API

Swagger/OpenAPI is available at `/swagger-ui.html` and `/v3/api-docs`.

Every error uses `{timestamp, status, code, message, path, requestId}`. Validation and malformed input return `400`; missing resources return `404`; domain, database-constraint, and optimistic-lock conflicts return `409`; authentication/authorization return `401`/`403`; and unexpected failures return a generic `500` without internal details.

- `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`
- `GET /api/v1/events` returns published events and supports `category`, `city`, `startDate`, `endDate`, `page`, `size`, and `sort`
- `GET /api/v1/events/{id}` and `/api/v1/events/{id}/seats`
- Public: paginated `GET /api/v1/venues` and `GET /api/v1/venues/{id}`
- Admin: venue CRUD, `POST /api/v1/venues/{venueId}/seats`, paginated protected user role/status management, and `/api/v1/admin/statistics`; administrators cannot demote/suspend themselves or remove the last active administrator
- Organizer/admin: `POST`, `PUT`, `DELETE /api/v1/events[/{id}]` and `PUT /api/v1/events/{eventId}/inventory`
- Customer: `POST /api/v1/bookings` with required `Idempotency-Key`, plus paginated list, detail, and cancellation endpoints
- Customer notifications: paginated `GET /api/v1/notifications`, `GET /api/v1/notifications/unread-count`, and `PATCH /api/v1/notifications/{id}/read`
- Paginated customer `GET /api/v1/tickets` returns booking, event, venue, and physical-seat context without extra public API calls. `GET /api/v1/tickets/{id}` returns the same projection only to the owning customer. QR tokens are returned only for `ACTIVE` tickets and are null for `USED` or `CANCELLED` tickets.
- Organizer/admin `POST /api/v1/tickets/validate` with `{ "qrToken": "...", "eventId": "..." }`. Administrators may validate any ticket; organizers are restricted to their own events. Validation rejects unknown, cancelled, previously used, wrong-event, and non-published-event tickets without changing ticket state.
- Organizer/admin `POST /api/v1/tickets/redeem` accepts the same request and atomically transitions an active ticket to `USED`. Concurrent scans are serialized by PostgreSQL: one succeeds and subsequent scans return `409 TICKET_ALREADY_USED`.
- Organizer `GET /api/v1/organizer/events/{eventId}/bookings` returns a bounded, paginated booking report with customer identity, financial totals, status, and seat IDs. Organizers can access only events they own; other events return `403 EVENT_ACCESS_DENIED`.
- Organizer `GET /api/v1/organizer/events`, `GET /api/v1/organizer/events/{eventId}`, and `GET /api/v1/organizer/events/{eventId}/inventory` provide ownership-scoped management reads for drafts and terminal events without exposing them through public discovery.
- Administrator `GET /api/v1/admin/bookings` lists bookings across the platform with optional `eventId` and `status` filters; `GET /api/v1/admin/bookings/{id}` returns management detail. `POST /api/v1/admin/bookings/{id}/cancel` performs the existing concurrency-safe refund/cancellation workflow and returns `204` when complete.
- Administrator `GET /api/v1/admin/events[/{id}]` exposes every event state for management, and `GET /api/v1/admin/venues/{venueId}/seats` provides a paginated physical-seat projection. These read endpoints add no new lifecycle transitions.

Collection endpoints for events, bookings, tickets, admin users, and notifications accept zero-based `page`, bounded `size`, and `sort=property,direction` parameters. The default page size is 20 and the maximum is 100. Booking, ticket, admin-user, and notification lists default to newest first; event search defaults to the nearest start time first. Responses use Spring's page envelope with `content`, page metadata, and total counts.

Customer booking list items include stable event/venue context and seat count. `GET /api/v1/bookings/{id}` is owner-scoped and returns the booking's event, venue, physical seats and booked prices, plus durable payment/refund state and timestamps. These historical projections remain available when the event is no longer publicly discoverable.

Use `tok_success` for an approved mock payment and `tok_fail` for a declined payment. `tok_unknown` simulates a provider response with no definitive outcome; the API returns `503 PAYMENT_OUTCOME_UNKNOWN`, retains the seat hold, and flags the durable payment for reconciliation. Do not retry an unknown outcome with a new idempotency key. No card data is accepted.

`Idempotency-Key` accepts 1–100 characters and is scoped to the authenticated user and booking-create operation. Repeating the same key and payload returns the original booking. Reusing that key with a different event, seat selection, or payment token returns `409 IDEMPOTENCY_PAYLOAD_MISMATCH`. Concurrent same-key requests are serialized by PostgreSQL and create only one booking.

New events must start as `DRAFT`; seat pricing must be configured before `PUBLISHED`. Public event and inventory reads expose published events only. Confirmed bookings may be cancelled more than 24 hours before an event; cancellation refunds the payment, cancels tickets, and releases seats.

`DELETE /api/v1/events/{id}` performs the organizer/admin cancellation transition. Cancelled events cannot be republished, have inventory reconfigured, or accept bookings. Every issued ticket is immediately returned with status `CANCELLED`, including when a provider refund needs follow-up. Confirmed bookings are sent through durable refunds; successful refunds release their inventory. A transient `409 EVENT_HAS_PENDING_BOOKINGS` requires cancellation to be retried after in-flight payment attempts finish.

Administrators can call `POST /api/v1/admin/outbox/{id}/retry` to reset a `FAILED` outbox event for immediate delivery. Non-failed or unknown event IDs return a conflict or not-found error respectively.
