# EventPass customer booking frontend

## Seat selection

The public `/events/:eventId/seats` experience reads the real inventory snapshot from `GET /api/v1/events/{eventId}/seats`. It groups seats by the backend's section and row values without assuming a rectangular or sequential venue layout. The UI supports every backend inventory state:

- `AVAILABLE` seats may be selected in the current browser view.
- `HELD` seats are temporarily held by a backend booking operation and cannot be selected.
- `SOLD` seats have been purchased and cannot be selected.
- `BLOCKED` seats are unavailable for sale and cannot be selected.
- `SELECTED` is a frontend-only presentation state. It does not create a Redis hold or reserve inventory.

The inventory query uses the existing TanStack Query key and 15-second foreground polling. If a selected seat becomes unavailable in a later snapshot, it is removed from the selection and the customer receives a clear warning.

The selection summary uses backend seat prices to show an estimated LKR total. The backend remains authoritative for availability and final pricing. The frontend mirrors the booking request's maximum of ten seat IDs.

Continuing requires an authenticated customer session and passes only the selected event and seat identifiers to protected `/checkout` route state. Reloading checkout deliberately returns a safe no-selection state instead of reconstructing or automatically submitting a booking.

## Checkout

Checkout refreshes the event and inventory, rejects selections that are no longer fully available, and shows a client estimate without sending that value to the backend. It submits the backend-supported `eventId`, `eventSeatIds`, and sandbox `paymentToken` fields to `POST /api/v1/bookings` with an `Idempotency-Key` header.

A cryptographically generated UUID remains stable for one logical checkout attempt, including manual retry after a timeout, network failure, rate limit, or ambiguous provider result. Only a definitive `PAYMENT_FAILED` result exposes an explicit action that creates a new payment attempt and key. Automatic mutation retries are disabled. Backend access-token refresh may replay the original request once through the shared API client with the same header.

Seat conflicts and unbookable events direct the customer back to refreshed inventory. The mock provider tokens `tok_success`, `tok_fail`, and `tok_unknown` support success, definitive failure, and ambiguous-result verification without collecting real card details. A confirmed response invalidates booking, event-detail, event-list, and seat-inventory queries, then hands off to the protected confirmation placeholder. Full booking confirmation, history, cancellation, tickets, and notifications remain later milestones.
