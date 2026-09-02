# EventPass frontend authentication

The `/login` and `/register` routes use the central typed API client against the backend's real authentication endpoints:

- `POST /api/v1/auth/login` accepts `email` and `password` and returns the backend authentication response.
- `POST /api/v1/auth/register` accepts `email`, `password`, `firstName`, and `lastName`, returns `201 Created`, and returns the same authentication response.

Both operations validate responses at runtime and use TanStack Query mutations with automatic retries disabled. Forms provide client-side required, email, password-length, and password-confirmation checks while treating backend validation as authoritative. Known backend codes such as `INVALID_CREDENTIALS`, `EMAIL_EXISTS`, and `RATE_LIMIT_EXCEEDED` are converted to safe customer-facing messages.

The backend returns an access token after successful login and registration and sets the rotating refresh token in a scoped `HttpOnly` cookie. EventPass keeps only the access token and non-sensitive session claims in a module-private in-memory vault. Tokens are never written to `localStorage`, `sessionStorage`, IndexedDB, URLs, logs, or browser-readable environment configuration.

## Session lifecycle

Successful login or registration installs the backend-issued session, clears previously cached authenticated data, and routes the user to a safe role-appropriate destination. The session provider exposes initializing, authenticated, unauthenticated, and refreshing states. Protected routes render an intentional loading state while identity is unknown or credentials are rotating.

The API client sends credentialed requests so the browser can attach the refresh cookie only within its server-defined scope. It attaches the current access token through its authentication transport. On startup, the session provider requests a CSRF token and attempts cookie-backed refresh. A protected request receiving `401` may start one refresh operation; concurrent failures share that operation. Successful rotation replaces the in-memory access token before each original request receives one bounded retry. The refresh request omits authentication recovery itself, preventing recursive refresh loops. Failed rotation clears credentials and cached customer, organizer, and administrator data, then protected navigation returns to login.

Refresh and logout send the CSRF cookie value in `X-XSRF-TOKEN`. Logout calls `POST /api/v1/auth/logout`, revokes the server-side token family, expires the refresh cookie, and always clears in-memory credentials and the TanStack Query cache. The backend remains authoritative.

A full browser reload or reopening EventPass restores a still-valid refresh session without exposing the refresh token to JavaScript. Expired, revoked, or replayed refresh sessions resolve to an unauthenticated state. Production sets `Secure`; `SameSite` is configurable and defaults to `Lax`, while the explicit CSRF token protects cookie-authenticated state changes.

Frontend route guards provide role-aware navigation for customer, organizer, administrator, and admission areas. They are a user-experience boundary only; Spring Security remains the authorization boundary.
