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

Seat conflicts and unbookable events direct the customer back to refreshed inventory. The mock provider tokens `tok_success`, `tok_fail`, and `tok_unknown` support success, definitive failure, and ambiguous-result verification without collecting real card details. A confirmed response invalidates booking, event-detail, event-list, and seat-inventory queries, then hands off to the protected confirmation route.

## Confirmation and booking management

The protected confirmation, history, and detail routes read bookings from `GET /api/v1/bookings` and `GET /api/v1/bookings/{id}` rather than relying on checkout navigation state. History requests 20 newest-first records per page. Event and seat presentation is composed from the public event and inventory endpoints when those resources remain available; the booking response remains usable when a cancelled or otherwise unavailable event can no longer be read publicly.

Customer cancellation calls `POST /api/v1/bookings/{id}/cancel` only after explicit confirmation. The interface mirrors the backend's confirmed-booking and 24-hour eligibility rule for guidance, prevents duplicate submissions, and treats the backend as authoritative. Successful cancellation invalidates booking, ticket, event, and inventory queries. Refund-pending or unknown outcomes tell the customer not to submit another cancellation.

The customer booking response does not expose separate payment, refund, provider-reference, or seat-detail objects. Payment/refund labels therefore describe only outcomes implied by the authoritative booking state; the frontend does not invent or persist unavailable financial data. Confirmed booking views link to `/tickets` using only the booking ID in ephemeral router state so the corresponding ticket is prioritized without exposing its QR token.
