# EventPass Frontend API Infrastructure

The Spring Boot backend is authoritative for authentication, authorization, pricing, availability, booking/payment state, and every business transition. Frontend types describe its transport contract; they do not duplicate its business rules.

## Base URL

`VITE_API_BASE_URL` accepts either the same-origin versioned path or an explicit HTTP(S) backend URL:

```env
VITE_API_BASE_URL=/api/v1
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

The same-origin value works with the Vite development proxy configured by `DEV_PROXY_TARGET`. Configuration is normalized once in `src/lib/environment.ts`; invalid protocols, embedded credentials, query strings, fragments, and URLs that do not target `/api/v1` fail clearly. A missing value uses the safe `/api/v1` default.

All `VITE_*` values are public browser configuration. They must never contain passwords, JWT secrets, access/refresh tokens, payment credentials, or private keys.

## Central API client

`src/services/api/apiClient.ts` is the single transport boundary. It provides typed `GET`, `POST`, `PUT`, `PATCH`, and `DELETE` methods with:

- JSON request and response handling;
- a 15-second default timeout with per-request override;
- caller cancellation through `AbortSignal`;
- explicit request headers and query parameters;
- response decoders that validate `unknown` JSON before returning application types;
- request, correlation, and rate-limit response metadata;
- safe normalized failures.

Endpoint paths must be root-relative to the configured `/api/v1` base. Query values are supplied separately through `buildQueryParams`, preventing URL construction from being scattered through features.

Feature services added later should call this client and provide a runtime response decoder. Pages and components should not call `fetch` directly.

## Authentication boundary

The client accepts an `AuthenticationTransport` that can supply an access token and react to a `401`. No token storage or fake identity is implemented here. The authentication feature will install the real transport after choosing session lifetime and refresh behavior.

Callers cannot inject an `Authorization` header through ordinary request configuration. Credentials are attached only through the authentication transport. The client does not log request bodies, authorization values, passwords, refresh tokens, QR tokens, or payment tokens.

## Error contract

The backend error envelope is:

```json
{
  "timestamp": "2026-08-28T00:00:00Z",
  "status": 409,
  "code": "CONCURRENT_MODIFICATION",
  "message": "The resource changed concurrently. Reload it and retry.",
  "path": "/api/v1/example",
  "requestId": "request-id"
}
```

`normalizeApiError` converts backend, timeout, cancellation, network, and unexpected transport failures into `ApiError`. Higher layers receive:

- HTTP status and backend code;
- a safe message;
- validation/authentication/authorization/not-found/conflict/rate-limit/server classification;
- retryability;
- request ID, correlation ID, path, and timestamp when available.

Raw non-JSON server bodies are never surfaced. The client handles at least `400`, `401`, `403`, `404`, `409`, `429`, `500`, and `503` consistently.

## Request and correlation IDs

The backend accepts and exposes `X-Request-Id` and `X-Correlation-Id`. The client generates a UUID when the browser supports it, sends both headers, and prefers the response values in `ApiResponse` and `ApiError`. This metadata can be shown in support/debugging UI without exposing sensitive request content.

Rate-limited endpoints may also expose `X-RateLimit-Limit` and `X-RateLimit-Remaining`; these are returned as optional response metadata.

## Pagination

Collection models follow the backend's Spring `Page` JSON shape: `content`, `pageable`, `totalPages`, `totalElements`, `last`, `size`, `number`, `sort`, `numberOfElements`, `first`, and `empty`.

The default page size is 20 and the backend maximum is 100. `paginatedResponseDecoder` validates the page envelope and delegates validation of every content item to the feature decoder.

## Server state

`ServerStateProvider` creates one TanStack Query client for the browser application. Defaults are intentionally small:

- queries are fresh for 30 seconds;
- unused data is collected after five minutes;
- reconnect refetching is enabled;
- focus refetching is disabled;
- retryable transport/server failures receive at most two retries with bounded exponential delay;
- permanent `400`, `401`, `403`, `404`, and `409` responses are not retried;
- mutations are not retried automatically.

`queryKeys` establishes stable conventions for real event, venue, booking, ticket, notification, organizer-report, and administrator resources.

## Booking creation

The booking feature service uses the central client for authenticated `POST /bookings` requests and strictly decodes the booking response. The checkout mutation sends an `Idempotency-Key` UUID with the backend request fields only; browser price estimates are never submitted as authoritative values. Mutations do not retry automatically, while a deliberate retry of an uncertain outcome preserves the same key. Successful creation invalidates the related booking, event, and inventory query families.

Checkout handles the backend's `SEAT_UNAVAILABLE`, `EVENT_NOT_BOOKABLE`, `PAYMENT_FAILED`, `PAYMENT_OUTCOME_UNKNOWN`, `IDEMPOTENCY_PAYLOAD_MISMATCH`, and rate-limit contracts without exposing raw provider or server details.

Customer booking management uses authenticated `GET /bookings`, `GET /bookings/{bookingId}`, and `POST /bookings/{bookingId}/cancel`. Lists explicitly request `createdAt,desc`, page size 20, and preserve page state in the URL. Cancellation has no client-generated financial payload and consumes the backend's empty `204` response. Success invalidates all customer booking queries plus affected ticket and event data; `BOOKING_NOT_CANCELLABLE`, `PAYMENT_NOT_REFUNDABLE`, `REFUND_PENDING`, `REFUND_FAILED`, and `REFUND_OUTCOME_UNKNOWN` remain authoritative workflow results.

## Customer tickets

The ticket feature uses authenticated `GET /tickets` with page size 20 and `issuedAt,desc` ordering. Its strict decoder accepts only the backend's `ACTIVE`, `USED`, and `CANCELLED` states. The API response's QR token is passed directly to the active-ticket QR renderer in memory and is never included in cache persistence, storage, URLs, logs, analytics, or error content. No client-side validation or redemption behavior is implemented.

## Actual backend areas represented

The shared types are derived from the current authentication, event, venue, seat/inventory, booking, ticket/admission, notification, organizer booking-report, administrator user/statistics/booking, pagination, security, and error controllers. There are no frontend payment or refund service models because the backend exposes those operations through booking workflows rather than public payment/refund endpoints.
