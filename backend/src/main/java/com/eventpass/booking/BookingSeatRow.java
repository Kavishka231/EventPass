package com.eventpass.booking;

import java.util.UUID;

public record BookingSeatRow(UUID bookingId, UUID eventSeatId) {}
