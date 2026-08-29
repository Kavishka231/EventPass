# EventPass frontend authentication

The `/login` and `/register` routes use the central typed API client against the backend's real authentication endpoints:

- `POST /api/v1/auth/login` accepts `email` and `password` and returns the backend authentication response.
- `POST /api/v1/auth/register` accepts `email`, `password`, `firstName`, and `lastName`, returns `201 Created`, and returns the same authentication response.

Both operations validate responses at runtime and use TanStack Query mutations with automatic retries disabled. Forms provide client-side required, email, password-length, and password-confirmation checks while treating backend validation as authoritative. Known backend codes such as `INVALID_CREDENTIALS`, `EMAIL_EXISTS`, and `RATE_LIMIT_EXCEEDED` are converted to safe customer-facing messages.

The backend issues an access token and rotating refresh token after both successful login and registration. EventPass keeps those credentials only in a module-private in-memory vault: they are never written to `localStorage`, `sessionStorage`, URLs, logs, or browser-readable environment configuration.

## Session lifecycle

Successful login or registration installs the backend-issued session, clears previously cached authenticated data, and routes the user to a safe role-appropriate destination. The session provider exposes initializing, authenticated, unauthenticated, and refreshing states. Protected routes render an intentional loading state while identity is unknown or credentials are rotating.

The API client attaches the current access token through its authentication transport. A protected request receiving `401` may start one refresh operation; concurrent failures share that operation. Successful rotation replaces both tokens before each original request receives one bounded retry. The refresh request omits authentication recovery itself, preventing recursive refresh loops. Failed rotation clears credentials and cached customer, organizer, and administrator data, then protected navigation returns to login with a session-expired message.

Logout calls `POST /api/v1/auth/logout` with the current refresh token when one exists and always clears local credentials and the TanStack Query cache. Backend logout and refresh-token-family revocation remain authoritative.

Because the backend returns tokens in JSON rather than an `HttpOnly` refresh cookie, a full browser reload cannot securely restore this in-memory session. Reloading therefore initializes as unauthenticated. Durable cross-reload restoration requires a backend cookie transport change and is not simulated with insecure browser storage.

Frontend route guards provide role-aware navigation for customer, organizer, administrator, and admission areas. They are a user-experience boundary only; Spring Security remains the authorization boundary.
