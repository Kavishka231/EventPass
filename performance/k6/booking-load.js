import http from "k6/http";
import { check, fail, group } from "k6";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";
import { SharedArray } from "k6/data";

const baseUrl = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const eventId = __ENV.EVENT_ID;
const customersFile = __ENV.CUSTOMERS_FILE || "./customers.json";
const customers = new SharedArray("load-test-customers", () => JSON.parse(open(customersFile)));
const duration = __ENV.TEST_DURATION || "1m";
const bookingVus = numberSetting("BOOKING_VUS", Math.min(customers.length, 5), 1);
const contentionSeats = numberSetting("CONTENDED_SEATS", 1, 1);
const successfulResponse = http.expectedStatuses(200);
const bookingResponses = http.expectedStatuses(201, 409);

const authenticationLatency = new Trend("eventpass_authentication_latency", true);
const eventBrowseLatency = new Trend("eventpass_event_browse_latency", true);
const seatBrowseLatency = new Trend("eventpass_seat_browse_latency", true);
const bookingLatency = new Trend("eventpass_booking_latency", true);
const authenticationSuccess = new Rate("eventpass_authentication_success");
const browsingSuccess = new Rate("eventpass_browsing_success");
const bookingSuccess = new Rate("eventpass_booking_success");
const unexpectedErrors = new Rate("eventpass_unexpected_errors");
const completedBookings = new Counter("eventpass_completed_bookings");
const seatContentionConflicts = new Counter("eventpass_seat_contention_conflicts");

export const options = {
  scenarios: {
    authentication: {
      executor: "constant-arrival-rate",
      exec: "authenticate",
      rate: numberSetting("AUTH_RATE", 2, 1),
      timeUnit: "1m",
      duration,
      preAllocatedVUs: numberSetting("AUTH_PREALLOCATED_VUS", 2, 1),
      maxVUs: numberSetting("AUTH_MAX_VUS", 5, 1),
    },
    event_browsing: {
      executor: "constant-arrival-rate",
      exec: "browseEvents",
      rate: numberSetting("BROWSE_RATE", 5, 1),
      timeUnit: "1s",
      duration,
      preAllocatedVUs: numberSetting("BROWSE_PREALLOCATED_VUS", 5, 1),
      maxVUs: numberSetting("BROWSE_MAX_VUS", 20, 1),
    },
    contested_booking: {
      executor: "per-vu-iterations",
      exec: "bookContestedSeat",
      vus: bookingVus,
      iterations: 1,
      maxDuration: __ENV.BOOKING_MAX_DURATION || "1m",
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    eventpass_unexpected_errors: ["rate<0.01"],
    eventpass_authentication_success: ["rate>0.99"],
    eventpass_browsing_success: ["rate>0.99"],
    eventpass_completed_bookings: ["count>0"],
    eventpass_authentication_latency: ["p(95)<750", "p(99)<1500"],
    eventpass_event_browse_latency: ["p(95)<500", "p(99)<1000"],
    eventpass_seat_browse_latency: ["p(95)<500", "p(99)<1000"],
    eventpass_booking_latency: ["p(95)<2000", "p(99)<3000"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  noConnectionReuse: false,
  userAgent: "EventPass-k6-booking-load/1.0",
};

let bookingAccessToken;

export function setup() {
  validateInputs();
  const eventResponse = http.get(`${baseUrl}/api/v1/events/${eventId}`, {
    tags: { operation: "setup_event" },
  });
  if (eventResponse.status !== 200) {
    fail(`EVENT_ID must identify a published event; received HTTP ${eventResponse.status}.`);
  }
  const seatsResponse = http.get(`${baseUrl}/api/v1/events/${eventId}/seats`, {
    tags: { operation: "setup_seats" },
  });
  if (seatsResponse.status !== 200) {
    fail(`Published event inventory could not be loaded; received HTTP ${seatsResponse.status}.`);
  }
  const availableSeatIds = seatsResponse
    .json()
    .filter((seat) => seat.availability === "AVAILABLE")
    .map((seat) => seat.id);
  if (availableSeatIds.length < contentionSeats) {
    fail(`The event needs at least ${contentionSeats} AVAILABLE seats for this test.`);
  }
  return { seatIds: availableSeatIds.slice(0, contentionSeats) };
}

export function authenticate() {
  const customer = customerForIteration();
  const response = login(customer, "authentication");
  const successful = response.status === 200 && Boolean(response.json("accessToken"));
  authenticationLatency.add(response.timings.duration);
  authenticationSuccess.add(successful);
  unexpectedErrors.add(!successful);
  check(response, { "authentication returns a token": () => successful });
}

export function browseEvents() {
  group("event browsing", () => {
    const events = http.get(`${baseUrl}/api/v1/events?page=0&size=20`, {
      tags: { operation: "list_events" },
    });
    const event = http.get(`${baseUrl}/api/v1/events/${eventId}`, {
      tags: { operation: "get_event" },
    });
    const seats = http.get(`${baseUrl}/api/v1/events/${eventId}/seats`, {
      tags: { operation: "list_event_seats" },
    });
    eventBrowseLatency.add(events.timings.duration + event.timings.duration);
    seatBrowseLatency.add(seats.timings.duration);
    const successful = events.status === 200 && event.status === 200 && seats.status === 200;
    browsingSuccess.add(successful);
    unexpectedErrors.add(!successful);
    check(events, { "event page is available": (response) => response.status === 200 });
    check(event, { "published event is available": (response) => response.status === 200 });
    check(seats, { "seat inventory is available": (response) => response.status === 200 });
  });
}

export function bookContestedSeat(data) {
  const customer = customers[(__VU - 1) % customers.length];
  if (!bookingAccessToken) {
    const loginResponse = login(customer, "booking_login");
    if (loginResponse.status !== 200 || !loginResponse.json("accessToken")) {
      unexpectedErrors.add(true);
      check(loginResponse, { "booking customer authenticates": () => false });
      return;
    }
    bookingAccessToken = loginResponse.json("accessToken");
  }
  const seatId = data.seatIds[(__VU - 1) % data.seatIds.length];
  const idempotencyKey = `k6-${runId()}-${__VU}-${seatId}`.slice(0, 100);
  const response = http.post(
    `${baseUrl}/api/v1/bookings`,
    JSON.stringify({ eventId, eventSeatIds: [seatId], paymentToken: "tok_success" }),
    {
      headers: {
        Authorization: `Bearer ${bookingAccessToken}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      responseCallback: bookingResponses,
      tags: { operation: "create_booking" },
    },
  );
  bookingLatency.add(response.timings.duration);
  const booked = response.status === 201;
  const contended = response.status === 409;
  bookingSuccess.add(booked);
  if (booked) completedBookings.add(1);
  if (contended) seatContentionConflicts.add(1);
  unexpectedErrors.add(!booked && !contended);
  check(response, {
    "booking succeeds or loses expected seat contention": () => booked || contended,
  });
}

function login(customer, operation) {
  return http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email: customer.email, password: customer.password }),
    {
      headers: { "Content-Type": "application/json" },
      responseCallback: successfulResponse,
      tags: { operation },
    },
  );
}

function customerForIteration() {
  return customers[exec.scenario.iterationInTest % customers.length];
}

function validateInputs() {
  if (!eventId) fail("EVENT_ID is required.");
  if (customers.length < bookingVus) {
    fail(`CUSTOMERS_FILE needs at least ${bookingVus} distinct customer accounts.`);
  }
  customers.forEach((customer, index) => {
    if (!customer.email || !customer.password) {
      fail(`Customer entry ${index} must contain email and password.`);
    }
  });
}

function numberSetting(name, fallback, minimum) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isInteger(value) || value < minimum) {
    throw new Error(`${name} must be an integer greater than or equal to ${minimum}.`);
  }
  return value;
}

function runId() {
  return (__ENV.RUN_ID || `${Date.now()}`).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 24);
}
