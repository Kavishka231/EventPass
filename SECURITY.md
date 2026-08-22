# Security

Passwords use BCrypt cost 12. Access JWTs last 15 minutes and use an explicitly configured HS256 key with a minimum 256-bit secret, signing key ID, issuer, audience, `JWT` header type, and `access` token-type claim; every value is required during validation. Opaque refresh tokens last 30 days and are stored only as SHA-256 hashes. Refresh rotates tokens. Authorization is enforced by Spring Security and method-level role checks, with organizer ownership checks in the service layer. The application never accepts prices or raw payment credentials and never logs credentials or tokens.

Suspended or disabled accounts cannot log in, refresh, or authenticate an existing JWT. Public registration always creates customers. A first administrator is created only when both bootstrap environment variables are explicitly supplied; public role elevation is impossible. Redis rate limits authentication and booking writes, while PostgreSQL remains authoritative for booking consistency.

Production secrets must be injected through environment variables. HTTPS and restrictive CORS should be configured at the deployment ingress for approved origins.
