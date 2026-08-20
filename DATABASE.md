# Database

PostgreSQL is the permanent source of truth. Flyway migration `V1__create_core_schema.sql` creates users, venues, venue seats, events, per-event seat inventory, bookings, booking items, payments, tickets, and hashed refresh tokens. `V2` adds the transactional outbox and idempotent-consumer ledger; `V3` permits a cancelled seat to receive a new ticket after resale. Foreign keys, checks, unique constraints, query indexes, and event-seat optimistic versions are explicit. Hibernate runs with `ddl-auto=validate` and Open Session in View is disabled.
