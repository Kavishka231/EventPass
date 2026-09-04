# Administrator workspace

The `/admin` workspace uses existing privileged APIs for platform oversight. It provides current system totals without charts or derived analytics.

## Users

Administrators can change roles and account status. The signed-in administrator's controls are disabled in the browser, while `ADMIN_SELF_LIFECYCLE_CHANGE` and `LAST_ACTIVE_ADMIN_REQUIRED` remain authoritative backend safeguards. Rejected lifecycle changes are shown as mutation failures and are never retried automatically.

## Venues and seats

Venue pages create and edit venue definitions, list physical seats with pagination, and add seat definitions within the configured capacity. Venue deletion remains subject to database references and backend validation. Event pricing and availability are not assigned here; those belong to event inventory.

## Events and bookings

The event administration list includes every lifecycle state. Administrators may update allowed event details or use the existing permanent cancellation transition. Booking administration lists platform bookings and exposes the existing eligibility-checked refund/cancellation workflow for confirmed bookings.

Every collection includes loading, empty, error/retry, and pagination behavior. Mutations display failures without weakening backend authorization, transition, concurrency, refund, or administrator lifecycle rules.
