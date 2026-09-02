# EventPass Frontend

EventPass is a React and TypeScript web application for the existing EventPass API. The frontend currently contains the production application foundation and reusable visual design system; customer, organizer, and administrator product flows will be implemented in later feature commits.

The frontend consumes the backend under `/api/v1`. Backend authorization, availability, inventory, booking and payment state, and server-calculated pricing remain authoritative.

## Current implementation

- React 19, TypeScript, Vite, and React Router application foundation
- Strict TypeScript project references and production source maps
- ESLint and Prettier configuration
- Environment-driven API base URL and local backend proxy
- Locally bundled Manrope variable font
- Light-first design tokens with compatible dark-surface overrides
- Responsive typography, spacing, radius, elevation, focus, and motion foundations
- Reusable buttons, form controls, surfaces, status feedback, dialog, toast, and layout primitives
- Responsive public, customer, organizer, administrator, and admission shells
- Declarative route groups with UX-only session and role guards
- Accessible desktop navigation, modal mobile navigation, skip links, and public footer
- Semantic placeholders for every planned route plus polished `403` and `404` states
- Central API transport with runtime response decoding, timeouts, cancellation, and typed safe errors
- Request/correlation ID propagation and rate-limit response metadata
- Backend-derived domain and Spring pagination transport types
- TanStack Query provider, transient-failure retry policy, and stable resource-key conventions
- Customer login and registration forms connected to the backend authentication API
- Client validation, safe authentication errors, accessible submission states, and duplicate-request protection
- In-memory authenticated sessions with rotating single-flight refresh and one bounded request retry
- Role-aware protected routes, safe return navigation, logout, session-expiration messaging, and authenticated cache isolation
- Editorial landing experience with live upcoming events from the public backend catalogue
- Event discovery with backend filtering, sorting, URL-backed pagination, and complete loading, empty, updating, and error states
- Public event details with backend seat-price bands, periodically refreshed availability snapshots, bookability-aware actions, and responsive booking presentation
- Interactive public seat maps with backend inventory states, accessible selection, ten-seat limit parity, estimated totals, polling conflict recovery, and authenticated checkout handoff
- Protected idempotent checkout with refreshed inventory validation, server-authoritative pricing, sandbox payment outcomes, stable retry keys, and related-query invalidation
- Refresh-safe booking confirmation, newest-first paginated history, enriched owned booking details, cancellation eligibility, duplicate-safe cancellation, and durable payment/refund feedback without browser-side event or inventory joins
- Secure paginated digital tickets backed by enriched owned projections, active-only QR presentation, booking/event/venue/seat context, clear used/cancelled states, and memory-only QR handling
- Customer notification center with newest-first pagination, accessible read state, synchronized unread navigation count, and authoritative mark-as-read handling
- Vitest, React Testing Library, user-event, jest-dom, MSW, and axe-core coverage for authentication, session recovery, discovery, booking, ticket security, notifications, and critical accessibility states
- Playwright Chromium coverage for the register-to-ticket customer journey and inactive-ticket QR secrecy using deterministic mocked backend API contracts

Route placeholders prove the remaining application hierarchy without implementing later product functionality. Access tokens stay only in memory. Browser sessions are restored through a rotating refresh token held in a scoped `HttpOnly` cookie; refresh and logout use the credentialed API transport and a CSRF cookie/header pair. No authentication token is stored in local storage, session storage, or IndexedDB. Admission scanning and organizer/administrator management features remain planned.

See [DESIGN.md](DESIGN.md) for the implemented visual language and component guidance.
See [API.md](API.md) for API configuration, transport, errors, pagination, authentication integration, and server-state conventions.
See [AUTH.md](AUTH.md) for login, registration, refresh, logout, protected-route, and token-handling behavior.
See [BOOKING.md](BOOKING.md) for seat-selection states, checkout idempotency, sandbox payment outcomes, and server-authoritative pricing.
See [TICKETS.md](TICKETS.md) for digital-ticket states, QR presentation, and token-security boundaries.
See [NOTIFICATIONS.md](NOTIFICATIONS.md) for customer notification pagination, read state, unread counts, and backend contract limits.

## Local development

Copy `.env.example` to `.env.local` when local values need to differ from the safe defaults, then run:

```shell
npm install
npm run dev
```

`VITE_API_BASE_URL` defaults to `/api/v1`. During development, Vite proxies `/api` requests to `DEV_PROXY_TARGET`, which defaults to `http://localhost:8080`. No credentials belong in frontend environment files; every `VITE_` value is public browser configuration.

## Verification

```shell
npm run format:check
npm test
npm run test:coverage
npm run lint
npm run typecheck
npm run build
npm run test:e2e
```

Unit and component tests use MSW at the network boundary with response fixtures matching the backend DTOs. Playwright builds and serves the production frontend, then intercepts `/api/v1` requests with the same deterministic contracts; it does not start or claim verification against a real backend. Backend Testcontainers coverage remains responsible for the real database, Redis, Kafka, HTTP, concurrency, and API-contract integration.

## Source structure

```text
frontend/
|-- src/
|   |-- app/          Application composition
|   |-- components/   Reusable layout, navigation, and UI primitives
|   |-- features/     Product modules, including authentication API operations and session contracts
|   |-- layouts/      Public and protected workspace shells
|   |-- lib/          Framework-independent helpers and environment access
|   |-- pages/        Route-level screens
|   |-- routes/       Route hierarchy and UX-only access guards
|   |-- test/         Vitest setup, MSW handlers, fixtures, utilities, and behavior/accessibility suites
|   `-- styles/       Tokens, base rules, components, and responsive layout
|-- e2e/              Playwright critical customer journeys
|-- .env.example
|-- eslint.config.js
|-- package.json
|-- tsconfig*.json
`-- vite.config.ts
```

Future feature folders should be added only when their product flows are implemented. Do not create fake API services or duplicate backend business rules in the browser.
