# EventPass customer booking frontend

## Seat selection

The public `/events/:eventId/seats` experience reads the real inventory snapshot from `GET /api/v1/events/{eventId}/seats`. It groups seats by the backend's section and row values without assuming a rectangular or sequential venue layout. The UI supports every backend inventory state:

- `AVAILABLE` seats may be selected in the current browser view.
- `HELD` seats are temporarily held by a backend booking operation and cannot be selected.
- `SOLD` seats have been purchased and cannot be selected.
- `BLOCKED` seats are unavailable for sale and cannot be selected.
- `SELECTED` is a frontend-only presentation state. It does not create a Redis hold or reserve inventory.

The inventory query uses the existing TanStack Query key and 15-second foreground polling. If a selected seat becomes unavailable in a later snapshot, it is removed from the selection and the customer receives a clear warning.

The selection summary uses backend seat prices to show an estimated LKR total. The backend remains authoritative for availability and final pricing. The frontend mirrors the booking request's maximum of ten seat IDs but does not submit `POST /api/v1/bookings` in this milestone.

Continuing requires an authenticated session and passes the selected event/seat identifiers to the protected checkout placeholder through router state. Checkout, idempotency-key creation, Redis acquisition, payment, booking confirmation, and tickets belong to later commits.
