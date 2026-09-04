# EventPass digital tickets

The protected `/tickets` route reads the authenticated customer's tickets from `GET /api/v1/tickets`. Results request 20 newest-first records per page using the backend's `issuedAt,desc` ordering.

Each ticket is composed with its owned booking plus public event and inventory information when those resources remain available. The ticket response remains authoritative for the ticket number, booking and event-seat identifiers, issue time, QR token, and status.

## Ticket states

- `ACTIVE` tickets render a scannable admission QR and clear event, venue, seat, booking, and issue information.
- `USED` tickets state that admission has already occurred and do not render the QR.
- `CANCELLED` tickets state that they are invalid and do not render the QR.

Frontend presentation is not admission validation. Authorized organizer or administrator staff must use the backend validation and redemption workflow, which remains authoritative and concurrency-safe.

## QR security

QR tokens exist only in the authenticated API response and the active in-memory React render. They are never written to local storage, session storage, cookies, navigation state, URL paths, query strings, application logs, analytics, documentation, or error messages. Booking pages pass only the booking ID in ephemeral router state when opening the ticket list. Inactive tickets do not render their QR token.
