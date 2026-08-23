# Booking load tests

This k6 suite exercises real EventPass HTTP endpoints for authentication, event browsing, seat inventory, contested booking, and concurrent customers. It does not seed organizer data. Run it against an isolated performance environment containing a future published event, priced available seats, and dedicated customer accounts.

## Prepare and run

1. Copy `customers.example.json` to `customers.json` and replace every entry with a distinct existing `CUSTOMER` account. The file is ignored by Git; source it from a secret manager in CI and delete it after the run.
2. Select a published event with enough available inventory. The suite discovers available event-seat IDs through the public API.
3. Use production-equivalent PostgreSQL, Redis, Kafka, JVM limits, and observability. Never run booking load tests against customer-facing production inventory.
4. Install k6 or use the official container image.

From the repository root:

```sh
mkdir -p performance-results
k6 run \
  --summary-export=performance-results/booking-load-summary.json \
  -e BASE_URL=https://eventpass-performance.example.com \
  -e EVENT_ID=00000000-0000-0000-0000-000000000000 \
  -e CUSTOMERS_FILE=./customers.json \
  -e TEST_DURATION=5m \
  -e BOOKING_VUS=5 \
  -e CONTENDED_SEATS=1 \
  performance/k6/booking-load.js
```

`BOOKING_VUS` distinct customers each attempt one booking concurrently. `CONTENDED_SEATS=1` directs all of them to the same available seat, so one successful booking and the remaining HTTP `409` responses represent correct concurrency protection. Increase `CONTENDED_SEATS` to measure parallel sales across a wider inventory pool. Use a fresh event or reset the isolated database between runs because successful bookings sell seats.

## Measurements and acceptance thresholds

k6 reports actual request throughput (`http_reqs` rate), HTTP failure rate, checks, transferred data, and request duration. The suite adds:

- `eventpass_authentication_latency`, `eventpass_event_browse_latency`, `eventpass_seat_browse_latency`, and `eventpass_booking_latency`, including p95 and p99;
- authentication and browsing success rates;
- completed booking throughput and booking success rate;
- expected seat-contention conflict count;
- unexpected error rate, which excludes correct contention `409` responses.

Default gates require less than 1% unexpected errors, greater than 99% authentication/browsing success, at least one completed booking, p95 authentication below 750 ms, p95 browsing below 500 ms, and p95 booking below 2 seconds. Treat these as initial service objectives and revise them only from reviewed capacity evidence. Preserve the JSON summary with the application version, environment size, database size, scenario settings, and dashboard links so results remain comparable.

Authentication defaults to two logins per minute because EventPass intentionally limits unauthenticated login traffic to ten requests per source IP per minute. Raising `AUTH_RATE` on a single generator primarily measures the rate limiter. For higher authentication throughput tests, use distributed k6 generators with an explicitly approved test plan; do not weaken production rate limiting.

Correlate results with `eventpass_*` Prometheus business metrics, JVM/HTTP metrics, PostgreSQL lock/query statistics, Redis latency, Kafka publication failures, and outbox backlog. A passing client-side latency summary is insufficient if queues or database locks continue growing after the run.
