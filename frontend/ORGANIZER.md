# Organizer workspace

Organizers use `/organizer` to manage only events owned by their authenticated account.

## Event workflow

Events are created as drafts using an existing administrator-managed venue. Draft details can be edited, inventory can be priced or blocked, and a configured draft can then be published. Cancellation is permanent and may trigger the backend's durable refund workflow. Published, cancelled, and completed events do not allow inventory changes.

## Inventory

The inventory page lists the physical seats already configured for the event venue. Selected seats are sent to `PUT /api/v1/events/{eventId}/inventory` with their authoritative price and blocked state. The backend remains responsible for validating ownership, venue membership, event state, and prices.

## Booking report

The report uses the ownership-scoped, paginated `GET /api/v1/organizer/events/{eventId}/bookings` endpoint. It shows customer identity, booking status, event-seat count, total, and creation time. It does not add analytics, exports, or client-side financial calculations.

Organizer pages include loading, empty, error/retry, mutation failure, and pagination states. A missing venue or physical seat inventory directs the organizer to an administrator instead of inventing organizer permissions.
