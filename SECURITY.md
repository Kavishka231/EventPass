# Security

Passwords use BCrypt cost 12. Access JWTs last 15 minutes; opaque refresh tokens last 30 days and are stored only as SHA-256 hashes. Refresh rotates tokens. Authorization is enforced by Spring Security and method-level role checks, with organizer ownership checks in the service layer. The application never accepts prices or raw payment credentials and never logs credentials or tokens.

Production secrets must be injected through environment variables. HTTPS and restrictive CORS should be configured at the deployment ingress for approved origins.
