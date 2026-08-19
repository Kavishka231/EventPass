# API

Swagger/OpenAPI is available at `/swagger-ui.html` and `/v3/api-docs`.

- `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`
- `GET /api/v1/events` supports `category`, `city`, `startDate`, `endDate`, `status`, `page`, `size`, and `sort`
- `GET /api/v1/events/{id}` and `/api/v1/events/{id}/seats`
- Organizer/admin: `POST`, `PUT`, `DELETE /api/v1/events[/{id}]`
- Customer: `POST /api/v1/bookings` with required `Idempotency-Key`, plus list, detail, and cancellation endpoints
- `GET /api/v1/tickets`

Use `tok_success` for an approved mock payment and `tok_fail` for a declined payment. No card data is accepted.
