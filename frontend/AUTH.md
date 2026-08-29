# EventPass frontend authentication

The `/login` and `/register` routes use the central typed API client against the backend's real authentication endpoints:

- `POST /api/v1/auth/login` accepts `email` and `password` and returns the backend authentication response.
- `POST /api/v1/auth/register` accepts `email`, `password`, `firstName`, and `lastName`, returns `201 Created`, and returns the same authentication response.

Both operations validate responses at runtime and use TanStack Query mutations with automatic retries disabled. Forms provide client-side required, email, password-length, and password-confirmation checks while treating backend validation as authoritative. Known backend codes such as `INVALID_CREDENTIALS`, `EMAIL_EXISTS`, and `RATE_LIMIT_EXCEEDED` are converted to safe customer-facing messages.

The backend currently issues an access token and rotating refresh token after both successful login and registration. This milestone deliberately does not persist those credentials or install them into the application session. Session restoration, refresh rotation, logout, protected-route integration, and the complete authentication lifecycle belong to the following authentication milestone.
