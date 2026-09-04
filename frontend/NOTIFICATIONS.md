# EventPass customer notifications

The protected `/notifications` route uses the authenticated customer endpoints:

- `GET /api/v1/notifications?page={page}&size=20&sort=createdAt,desc`
- `GET /api/v1/notifications/unread-count`
- `PATCH /api/v1/notifications/{notificationId}/read`

The list is server-paginated and newest-first. Unread entries have both a visible label and a structural accent, while read entries show their read date. Mark-as-read waits for the authoritative `204` response before invalidating notification lists and the navigation count; failed mutations leave the item unread.

The customer navigation fetches the unread count through TanStack Query, hides zero, caps visible badge text at `99+`, and refreshes at a restrained 60-second interval. Authentication logout clears this in-memory cache.

## Contract limitations

The customer response contains `id`, `type`, `title`, `message`, `createdAt`, and nullable `readAt`. It does not expose internal delivery status, source event ID, booking ID, event ID, payment ID, ticket ID, or an action link. Consequently, the frontend does not present `PENDING`, `PROCESSING`, `DELIVERED`, or `FAILED` delivery states and does not construct related-resource links. Unknown notification types use a neutral category label without failing runtime decoding.

Notification title and message content are rendered as plain React text. The feature does not use HTML injection, accept user IDs, persist notification payloads in browser storage, log payloads, or expose authentication, payment, or QR-token data in URLs.
