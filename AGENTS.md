# AGENTS.md — Ticket Booking Platform Backend

## 1. Project Overview

Build a production-grade, cloud-native ticket booking platform backend.

The system allows users to:

- Register and authenticate
- Browse events
- View event details
- View venues and seats
- Select seats
- Temporarily lock seats
- Make bookings
- Process payments through a sandbox/mock provider
- Receive digital tickets
- View booking history
- Cancel eligible bookings

Organizers can:

- Create events
- Update events
- Manage venues
- Configure ticket pricing
- View bookings

Administrators can:

- Manage users
- Manage events
- Manage venues
- Manage bookings
- View system-level statistics

The backend must be designed as a production-quality system rather than a basic CRUD application.

The most important technical challenge is preventing double booking under concurrent requests.

---

# 2. Primary Goals

The backend must demonstrate:

1. Clean architecture
2. SOLID principles
3. RESTful API design
4. Secure authentication and authorization
5. PostgreSQL persistence
6. Redis caching and distributed seat locking
7. Kafka event-driven communication
8. Transactional booking operations
9. Idempotent payment/booking operations
10. Comprehensive automated testing
11. Structured logging
12. Health checks
13. Metrics
14. Docker compatibility
15. Production-ready configuration
16. API documentation
17. Error handling
18. Input validation
19. Concurrency safety
20. Observability

Do not implement features merely to increase complexity.

Every technology must have a clear purpose.

---

# 3. Technology Stack

Use the following technologies unless there is a strong technical reason not to.

## Backend

- Java 21 LTS
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Data JPA
- Hibernate
- Bean Validation
- Spring Actuator

## Database

- PostgreSQL

## Database migrations

- Flyway

Do NOT rely on Hibernate automatic schema generation in production.

Use:

    spring.jpa.hibernate.ddl-auto=validate

Database schema changes must be handled through Flyway migrations.

## Cache and distributed locking

- Redis
- Spring Data Redis

Redis is primarily used for:

- Temporary seat locks
- Short-lived caching
- Rate limiting where appropriate

## Messaging

- Apache Kafka
- Spring Kafka

Kafka should be used for asynchronous domain events such as:

- BOOKING_CREATED
- PAYMENT_COMPLETED
- PAYMENT_FAILED
- TICKET_GENERATED
- BOOKING_CANCELLED
- SEAT_RELEASED

## Authentication

- Spring Security
- JWT access tokens
- Refresh tokens

## Documentation

- OpenAPI
- Swagger UI

## Testing

- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers
- MockMvc
- REST Assured where useful

## Code quality

- Checkstyle or Spotless
- SonarQube-compatible code
- No compiler warnings should be ignored without justification

---

# 4. Architecture

Use a modular monolith architecture initially.

Do NOT create unnecessary microservices.

The codebase must be structured so modules can later be extracted into independent services.

Recommended modules:

    auth
    user
    event
    venue
    seat
    booking
    payment
    ticket
    notification
    common

Recommended package structure:

    com.ticketplatform
    ├── auth
    │   ├── controller
    │   ├── service
    │   ├── repository
    │   ├── entity
    │   ├── dto
    │   └── security
    │
    ├── user
    ├── event
    ├── venue
    ├── seat
    ├── booking
    ├── payment
    ├── ticket
    ├── notification
    └── common

Keep business logic out of controllers.

Controllers must only handle:

- HTTP requests
- Validation
- Authentication context
- Calling application services
- HTTP responses

Business logic belongs in services/domain components.

---

# 5. Domain Model

The core entities should include:

## User

Fields:

- id
- email
- passwordHash
- firstName
- lastName
- role
- status
- createdAt
- updatedAt

Roles:

    CUSTOMER
    ORGANIZER
    ADMIN

---

## Event

Fields:

- id
- name
- description
- category
- startDateTime
- endDateTime
- venueId
- status
- createdAt
- updatedAt

Statuses:

    DRAFT
    PUBLISHED
    CANCELLED
    COMPLETED

---

## Venue

Fields:

- id
- name
- address
- city
- capacity
- createdAt
- updatedAt

---

## Seat

Fields:

- id
- venueId
- section
- rowNumber
- seatNumber
- seatType
- status

Seat types may include:

    REGULAR
    PREMIUM
    VIP

---

## EventSeat

An event must have its own seat inventory.

Do NOT assume the global Seat table alone represents availability.

EventSeat should contain:

- id
- eventId
- seatId
- price
- status
- version

Statuses:

    AVAILABLE
    HELD
    SOLD
    BLOCKED

Use optimistic locking where appropriate.

Example:

    @Version
    private Long version;

---

## Booking

Fields:

- id
- bookingReference
- userId
- eventId
- status
- totalAmount
- currency
- expiresAt
- createdAt
- updatedAt

Statuses:

    PENDING
    CONFIRMED
    CANCELLED
    EXPIRED
    FAILED

Booking references must be unique.

---

## BookingItem

Fields:

- id
- bookingId
- eventSeatId
- unitPrice

A booking can contain multiple seats.

---

## Payment

Fields:

- id
- bookingId
- paymentReference
- amount
- currency
- status
- provider
- createdAt
- updatedAt

Statuses:

    PENDING
    SUCCESS
    FAILED
    REFUNDED

Payment operations must be idempotent.

---

## Ticket

Fields:

- id
- ticketNumber
- bookingId
- eventSeatId
- qrToken
- status
- issuedAt
- usedAt

Statuses:

    ACTIVE
    USED
    CANCELLED

QR tokens must be cryptographically secure and unique.

---

# 6. Authentication

Implement:

    POST /api/v1/auth/register
    POST /api/v1/auth/login
    POST /api/v1/auth/refresh
    POST /api/v1/auth/logout

Passwords must NEVER be stored as plaintext.

Use a strong password hashing algorithm supported by Spring Security.

JWT access tokens should contain:

- user ID
- role
- expiration
- issued-at

Do not put sensitive information inside JWTs.

Refresh tokens must be handled securely.

Access tokens should have a relatively short lifetime.

---

# 7. Authorization

Use role-based authorization.

Example:

    CUSTOMER
    ORGANIZER
    ADMIN

Customers may:

- Browse events
- Create bookings
- View their bookings
- Cancel eligible bookings
- View their tickets

Organizers may:

- Create events
- Update their events
- Manage event configuration
- View event bookings

Admins may:

- Manage all users
- Manage all events
- Manage venues
- View system-level information

Never rely only on frontend authorization.

Authorization must be enforced server-side.

---

# 8. Event APIs

Implement:

    GET    /api/v1/events
    GET    /api/v1/events/{id}
    POST   /api/v1/events
    PUT    /api/v1/events/{id}
    DELETE /api/v1/events/{id}

Support filtering:

    category
    city
    startDate
    endDate
    status

Support pagination.

Use:

    page
    size
    sort

Do not return database entities directly from controllers.

Use DTOs.

---

# 9. Seat APIs

Implement:

    GET /api/v1/events/{eventId}/seats

The response should include:

- Seat ID
- Section
- Row
- Seat number
- Type
- Price
- Availability

Do not expose internal database details unnecessarily.

---

# 10. Seat Locking

This is one of the most important requirements.

When a customer selects a seat:

    AVAILABLE
        ↓
    HELD
        ↓
    Payment
        ↓
    SOLD

The temporary lock should expire automatically.

Recommended default:

    5 minutes

Redis should maintain the temporary lock.

Example logical key:

    seat-lock:{eventId}:{seatId}

The lock value should identify the booking/session that owns it.

Use Redis TTL.

Never trust the client to release or extend locks.

---

# 11. Double Booking Prevention

The system MUST prevent two users from purchasing the same seat.

Consider concurrent requests:

    User A → Seat A10
    User B → Seat A10

Only one request may successfully acquire the seat.

Use multiple layers of protection where appropriate:

1. Redis distributed locking
2. Database transaction
3. Database constraints
4. Optimistic/pessimistic locking where appropriate
5. Final availability validation

Do not rely solely on Redis.

The database remains the source of truth for permanent booking state.

---

# 12. Booking Flow

Recommended flow:

    User selects seats
            ↓
    Validate event
            ↓
    Validate seat availability
            ↓
    Acquire Redis locks
            ↓
    Create PENDING booking
            ↓
    Set expiration
            ↓
    Payment
            ↓
       ┌────┴────┐
       ↓         ↓
    SUCCESS    FAILURE
       ↓         ↓
    Confirm    Cancel
       ↓         ↓
    SOLD       Release seats
       ↓
    Generate tickets
       ↓
    Publish Kafka events

Do not mark seats SOLD before successful payment unless the business flow explicitly requires it.

---

# 13. Booking APIs

Implement:

    POST /api/v1/bookings
    GET /api/v1/bookings
    GET /api/v1/bookings/{id}
    POST /api/v1/bookings/{id}/cancel

The create booking request should contain:

- event ID
- selected seat IDs
- payment information/reference where appropriate

Never trust the submitted price.

The backend must calculate the final price from the database.

---

# 14. Idempotency

Booking creation and payment operations must support idempotency.

Support:

    Idempotency-Key

For example:

    POST /api/v1/bookings

with:

    Idempotency-Key: unique-client-generated-key

Repeated requests using the same key must not create duplicate bookings.

Payment callbacks/webhooks must also be idempotent.

---

# 15. Payment

Initially implement a mock payment provider.

Create an abstraction:

    PaymentProvider

Example:

    PaymentProvider
        └── MockPaymentProvider

The application must not tightly couple booking logic to a specific payment provider.

Later implementations could include:

    StripePaymentProvider
    PayHerePaymentProvider

Never store:

- Card numbers
- CVV
- Full payment credentials

The backend should only handle provider references/tokens.

---

# 16. Kafka

Use domain events.

Example:

    BookingCreatedEvent
    PaymentCompletedEvent
    PaymentFailedEvent
    TicketGeneratedEvent
    BookingCancelledEvent
    SeatReleasedEvent

Kafka topics:

    booking.events
    payment.events
    ticket.events
    notification.events

Consumers should be idempotent.

A duplicate Kafka event must not create duplicate tickets or notifications.

---

# 17. Outbox Pattern

Where practical, use the transactional outbox pattern.

Problem:

    Database transaction succeeds
    Kafka publish fails

This can create inconsistent state.

Preferred flow:

    DB Transaction
         ↓
    Save business data
         ↓
    Save Outbox Event
         ↓
    Commit
         ↓
    Outbox Publisher
         ↓
    Kafka

Implement the outbox pattern for important domain events if project scope allows it.

---

# 18. Database Rules

Use PostgreSQL.

Rules:

- Foreign keys must be explicit
- Unique constraints must be used
- Index frequently queried columns
- Avoid N+1 queries
- Use transactions where required
- Never expose database entities directly
- Use migrations
- Avoid unnecessary eager relationships

Important indexes may include:

- user.email
- event.startDateTime
- event.status
- booking.bookingReference
- booking.userId
- booking.eventId
- eventSeat.eventId
- eventSeat.status
- payment.paymentReference
- ticket.ticketNumber

---

# 19. API Versioning

All public APIs must use:

    /api/v1/...

Do not create unversioned production endpoints.

---

# 20. Error Handling

Use centralized exception handling.

Use:

    @RestControllerAdvice

Return consistent error responses.

Example:

    {
      "timestamp": "...",
      "status": 409,
      "error": "SEAT_UNAVAILABLE",
      "message": "One or more selected seats are no longer available.",
      "path": "/api/v1/bookings",
      "requestId": "..."
    }

Do not expose:

- Stack traces
- SQL errors
- Internal class names
- Database details
- Secrets

Production errors must be generic.

---

# 21. HTTP Status Codes

Use appropriate HTTP status codes.

Examples:

    200 OK
    201 CREATED
    204 NO_CONTENT
    400 BAD_REQUEST
    401 UNAUTHORIZED
    403 FORBIDDEN
    404 NOT_FOUND
    409 CONFLICT
    422 UNPROCESSABLE_ENTITY
    429 TOO_MANY_REQUESTS
    500 INTERNAL_SERVER_ERROR
    503 SERVICE_UNAVAILABLE

Do not return 200 for every operation.

---

# 22. Validation

Use Bean Validation.

Examples:

    @NotBlank
    @Email
    @Size
    @Positive
    @Future

Validate all externally supplied data.

Never trust frontend validation.

---

# 23. Logging

Use structured JSON logging in production.

Every request should have:

    requestId
    correlationId
    timestamp
    service
    HTTP method
    endpoint
    status
    latency

Authenticated requests should include user ID where appropriate.

NEVER log:

- Passwords
- JWT tokens
- Refresh tokens
- Payment secrets
- Sensitive personal information

---

# 24. Observability

Spring Boot Actuator must be enabled.

Provide:

    /actuator/health
    /actuator/info
    /actuator/prometheus

Separate:

    liveness
    readiness

The application must expose useful metrics.

Important metrics:

- HTTP request count
- HTTP error count
- request latency
- booking attempts
- successful bookings
- failed bookings
- seat lock failures
- payment failures
- Kafka consumer failures

---

# 25. Health Checks

Health checks must verify critical dependencies.

At minimum:

    PostgreSQL
    Redis
    Kafka

Do not make liveness depend on every external service.

A temporary database failure should not necessarily cause Kubernetes to restart every application pod.

Use readiness appropriately.

---

# 26. Security Requirements

Implement:

- Password hashing
- JWT validation
- Role-based authorization
- Input validation
- Rate limiting
- Secure HTTP headers
- CORS configuration
- HTTPS in production
- Secret injection through environment/configuration
- Dependency vulnerability scanning

Never hardcode:

- JWT secrets
- Database passwords
- API keys
- Payment credentials
- Kafka credentials

Never commit secrets to Git.

---

# 27. Configuration

Use environment-specific configuration.

Support:

    application.yml
    application-dev.yml
    application-test.yml
    application-prod.yml

Configuration should come from environment variables/secrets where appropriate.

Example:

    DATABASE_URL
    DATABASE_USERNAME
    DATABASE_PASSWORD
    REDIS_URL
    KAFKA_BOOTSTRAP_SERVERS
    JWT_SECRET

Never commit production credentials.

Provide:

    .env.example

with safe placeholder values.

---

# 28. Testing Strategy

Minimum testing layers:

## Unit tests

Test:

- Services
- Validators
- Domain logic
- Pricing logic
- Booking rules

## Repository tests

Use PostgreSQL Testcontainers where database behavior matters.

## Integration tests

Test:

    API
    PostgreSQL
    Redis
    Kafka

Use Testcontainers rather than mocking infrastructure when realistic integration behavior is required.

## E2E tests

Test critical flow:

    Register
    ↓
    Login
    ↓
    Browse event
    ↓
    Select seat
    ↓
    Lock seat
    ↓
    Create booking
    ↓
    Payment
    ↓
    Ticket generation

---

# 29. Concurrency Tests

This is mandatory.

Create tests where multiple users attempt to book the same seat simultaneously.

Example:

    100 concurrent requests
           ↓
       Seat A10
           ↓
    exactly ONE successful booking

The test must verify:

- No duplicate booking
- No duplicate ticket
- No inconsistent seat status
- Correct failure responses

This is a major acceptance criterion.

---

# 30. Load Testing

The backend must be designed to support load testing with k6.

Create scenarios for:

    Event browsing
    Seat availability
    Seat locking
    Booking
    Authentication

The project should eventually document:

- Requests per second
- Average latency
- P95 latency
- P99 latency
- Error rate
- Maximum tested concurrency

Do not invent performance numbers.

Only report measured results.

---

# 31. Docker

The backend must be containerizable.

Requirements:

- Multi-stage Dockerfile
- Non-root runtime user
- Minimal runtime image
- Health check
- No secrets inside image
- Environment-driven configuration

Example runtime architecture:

    Docker
      ↓
    Spring Boot
      ↓
    PostgreSQL
    Redis
    Kafka

Provide Docker Compose for local development.

---

# 32. CI Requirements

The project must eventually support CI that performs:

    Checkout
    ↓
    Java setup
    ↓
    Dependency caching
    ↓
    Compile
    ↓
    Unit tests
    ↓
    Integration tests
    ↓
    Static analysis
    ↓
    Dependency scan
    ↓
    Docker build
    ↓
    Container vulnerability scan

CI must fail if tests fail.

Do not disable tests merely to make CI green.

---

# 33. Code Quality

Follow:

- SOLID
- DRY
- KISS
- Clean Code
- Meaningful naming
- Small focused classes
- Dependency inversion
- Constructor injection

Avoid:

- God classes
- Massive controllers
- Static global state
- Business logic in repositories
- Business logic in controllers
- Duplicate logic
- Magic numbers
- Hardcoded configuration

Prefer composition over inheritance unless inheritance is genuinely appropriate.

---

# 34. DTO Rules

Never expose JPA entities directly through public APIs.

Use:

    Request DTO
        ↓
    Service
        ↓
    Entity
        ↓
    Service
        ↓
    Response DTO

Separate:

    CreateEventRequest
    UpdateEventRequest
    EventResponse

Do not reuse one DTO for every operation if their requirements differ.

---

# 35. Transaction Rules

Use transactions intentionally.

Examples requiring transactional boundaries:

- Creating a booking
- Confirming a booking
- Cancelling a booking
- Updating permanent seat availability
- Processing important state transitions

Do not put @Transactional everywhere without understanding transaction boundaries.

---

# 36. Seat State Machine

Seat transitions must be explicit.

Allowed examples:

    AVAILABLE → HELD
    HELD → SOLD
    HELD → AVAILABLE
    SOLD → CANCELLED
    AVAILABLE → BLOCKED

Invalid transitions must be rejected.

Do not allow arbitrary status changes through APIs.

---

# 37. Booking State Machine

Use controlled transitions.

Example:

    PENDING → CONFIRMED
    PENDING → FAILED
    PENDING → EXPIRED
    CONFIRMED → CANCELLED

Invalid transitions must fail safely.

---

# 38. Project Documentation

Maintain:

    README.md
    ARCHITECTURE.md
    API.md
    DATABASE.md
    SECURITY.md
    TESTING.md
    DEPLOYMENT.md

Documentation should explain:

- Architecture
- Technology decisions
- Database design
- Booking flow
- Seat locking
- Concurrency strategy
- Kafka events
- Redis usage
- Security
- Testing
- Deployment

---

# 39. Development Rules

Before implementing a feature:

1. Understand existing architecture.
2. Inspect related modules.
3. Reuse existing abstractions.
4. Avoid unnecessary dependencies.
5. Implement the smallest correct change.
6. Add tests.
7. Run relevant tests.
8. Check formatting/static analysis.
9. Update documentation when behavior changes.

Do not rewrite unrelated code.

Do not modify existing working functionality without a reason.

---

# 40. Git Rules

Use conventional commits.

Examples:

    feat(booking): add seat reservation
    feat(auth): implement refresh tokens
    fix(seat): prevent duplicate reservation
    test(booking): add concurrent booking tests
    chore(ci): add integration test pipeline

Keep commits focused.

Do not mix unrelated features into one commit.

---

# 41. Definition of Done

A backend feature is NOT complete until:

- Implementation is complete
- Input validation exists
- Authorization is correct
- Error handling exists
- Unit tests exist
- Integration tests exist where appropriate
- Database migration exists if schema changed
- API documentation is updated
- Logging is appropriate
- No secrets are introduced
- Existing tests still pass
- Code formatting passes
- Static analysis passes

---

# 42. Implementation Order

Implement the backend in this order:

## Phase 1

Project initialization:

- Spring Boot
- Maven
- PostgreSQL
- Flyway
- Docker Compose
- Base configuration

## Phase 2

Authentication:

- User entity
- Registration
- Login
- JWT
- Refresh tokens
- Roles
- Security configuration

## Phase 3

Event management:

- Event
- Venue
- Seat
- EventSeat
- CRUD APIs
- Validation
- Pagination

## Phase 4

Booking:

- Booking
- BookingItem
- Seat availability
- Transactions
- Redis seat locking
- Expiration

## Phase 5

Payment:

- Payment entity
- Payment provider abstraction
- Mock payment provider
- Idempotency
- Payment state transitions

## Phase 6

Tickets:

- Ticket generation
- QR token
- Ticket validation
- Ticket cancellation

## Phase 7

Kafka:

- Domain events
- Producers
- Consumers
- Idempotent consumers
- Notification flow

## Phase 8

Observability:

- Actuator
- Prometheus metrics
- Structured logging
- Correlation IDs
- Health/readiness endpoints

## Phase 9

Testing:

- Unit tests
- Integration tests
- Testcontainers
- Concurrency tests
- E2E tests

## Phase 10

Production readiness:

- Docker hardening
- CI
- Security scanning
- Production configuration
- Deployment documentation

---

# 43. Important Agent Rules

The coding agent MUST:

- Inspect existing files before creating new ones.
- Follow the existing architecture.
- Prefer existing dependencies.
- Avoid unnecessary libraries.
- Never hardcode credentials.
- Never disable security to solve a development problem.
- Never remove tests to make them pass.
- Never silently ignore failing tests.
- Never introduce duplicate implementations.
- Never expose entities directly from controllers.
- Never trust client-provided prices.
- Never rely solely on Redis for permanent booking state.
- Never allow duplicate bookings.
- Never claim a feature works without testing it.
- Never invent performance results.
- Never modify unrelated files.

If a requirement is ambiguous, choose the safest production-quality interpretation and document the assumption.

---

# 44. Initial Acceptance Criteria

The first complete backend milestone must support:

    User registration
    ↓
    User login
    ↓
    Browse published events
    ↓
    View event seats
    ↓
    Select seats
    ↓
    Acquire temporary Redis lock
    ↓
    Create booking
    ↓
    Mock payment
    ↓
    Confirm booking
    ↓
    Mark seats SOLD
    ↓
    Generate ticket
    ↓
    Retrieve ticket

The system must demonstrate that two concurrent users cannot successfully purchase the same seat.

This concurrency requirement is a core system requirement, not an optional enhancement.

---

# 45. Engineering Priority

When making implementation decisions, prioritize:

1. Correctness
2. Security
3. Data consistency
4. Testability
5. Maintainability
6. Observability
7. Performance
8. Scalability
9. Developer convenience

Do not sacrifice correctness for premature optimization.

The backend should be simple enough to understand, but robust enough to demonstrate production-grade software engineering and DevOps practices.