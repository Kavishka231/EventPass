package com.eventpass.booking;

import com.eventpass.common.metrics.BusinessMetrics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpirationJob {
  private final BookingService bookings;
  private final BusinessMetrics metrics;

  public BookingExpirationJob(BookingService bookings, BusinessMetrics metrics) {
    this.bookings = bookings;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${eventpass.booking.expiration-scan-delay:PT30S}")
  public void expire() {
    metrics.bookingsExpired(bookings.expirePendingBookings());
  }
}
