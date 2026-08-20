package com.eventpass.booking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpirationJob {
  private final BookingService bookings;

  public BookingExpirationJob(BookingService bookings) {
    this.bookings = bookings;
  }

  @Scheduled(fixedDelayString = "${eventpass.booking.expiration-scan-delay:PT30S}")
  public void expire() {
    bookings.expirePendingBookings();
  }
}
