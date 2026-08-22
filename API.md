# API

Swagger/OpenAPI is available at `/swagger-ui.html` and `/v3/api-docs`.

- `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`
- `GET /api/v1/events` supports `category`, `city`, `startDate`, `endDate`, `status`, `page`, `size`, and `sort`
- `GET /api/v1/events/{id}` and `/api/v1/events/{id}/seats`
- Public: paginated `GET /api/v1/venues` and `GET /api/v1/venues/{id}`
- Admin: venue CRUD, `POST /api/v1/venues/{venueId}/seats`, user role/status management, and `/api/v1/admin/statistics`
- Organizer/admin: `POST`, `PUT`, `DELETE /api/v1/events[/{id}]` and `PUT /api/v1/events/{eventId}/inventory`
- Customer: `POST /api/v1/bookings` with required `Idempotency-Key`, plus list, detail, and cancellation endpoints
- Customer notifications: paginated `GET /api/v1/notifications`, `GET /api/v1/notifications/unread-count`, and `PATCH /api/v1/notifications/{id}/read`
- `GET /api/v1/tickets`

Use `tok_success` for an approved mock payment and `tok_fail` for a declined payment. `tok_unknown` simulates a provider response with no definitive outcome; the API returns `503 PAYMENT_OUTCOME_UNKNOWN`, retains the seat hold, and flags the durable payment for reconciliation. Do not retry an unknown outcome with a new idempotency key. No card data is accepted.

`Idempotency-Key` accepts 1–100 characters and is scoped to the authenticated user and booking-create operation. Repeating the same key and payload returns the original booking. Reusing that key with a different event, seat selection, or payment token returns `409 IDEMPOTENCY_PAYLOAD_MISMATCH`. Concurrent same-key requests are serialized by PostgreSQL and create only one booking.

New events must start as `DRAFT`; seat pricing must be configured before `PUBLISHED`. Public event and inventory reads expose published events only. Confirmed bookings may be cancelled more than 24 hours before an event; cancellation refunds the payment, cancels tickets, and releases seats.

`DELETE /api/v1/events/{id}` performs the organizer/admin cancellation transition. Cancelled events cannot be republished, have inventory reconfigured, or accept bookings. Every issued ticket is immediately returned with status `CANCELLED`, including when a provider refund needs follow-up. Confirmed bookings are sent through durable refunds; successful refunds release their inventory. A transient `409 EVENT_HAS_PENDING_BOOKINGS` requires cancellation to be retried after in-flight payment attempts finish.

Administrators can call `POST /api/v1/admin/outbox/{id}/retry` to reset a `FAILED` outbox event for immediate delivery. Non-failed or unknown event IDs return a conflict or not-found error respectively.
