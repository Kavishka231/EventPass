package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingService {
  private final BookingRepository bookings;
  private final BookingItemRepository bookingItems;
  private final BookingService bookingService;

  public AdminBookingService(
      BookingRepository bookings,
      BookingItemRepository bookingItems,
      BookingService bookingService) {
    this.bookings = bookings;
    this.bookingItems = bookingItems;
    this.bookingService = bookingService;
  }

  @Transactional(readOnly = true)
  public Page<AdminBookingController.AdminBookingResponse> list(
      UUID eventId, Booking.Status status, Pageable pageable) {
    Page<AdminBookingRow> page = bookings.findManagementRows(eventId, status, pageable);
    Map<UUID, List<UUID>> seatsByBooking =
        seatsByBooking(page.getContent().stream().map(AdminBookingRow::id).toList());
    return page.map(row -> response(row, seatsByBooking.getOrDefault(row.id(), List.of())));
  }

  @Transactional(readOnly = true)
  public AdminBookingController.AdminBookingResponse get(UUID id) {
    AdminBookingRow row =
        bookings
            .findManagementRowById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found."));
    return response(row, seatsByBooking(List.of(id)).getOrDefault(id, List.of()));
  }

  public void cancel(UUID id, User administrator) {
    bookingService.cancel(id, administrator);
  }

  private Map<UUID, List<UUID>> seatsByBooking(List<UUID> bookingIds) {
    if (bookingIds.isEmpty()) return Map.of();
    return bookingItems.findSeatRowsByBookingIds(bookingIds).stream()
        .collect(
            Collectors.groupingBy(
                BookingSeatRow::bookingId,
                LinkedHashMap::new,
                Collectors.mapping(BookingSeatRow::eventSeatId, Collectors.toList())));
  }

  private AdminBookingController.AdminBookingResponse response(
      AdminBookingRow row, List<UUID> eventSeatIds) {
    return new AdminBookingController.AdminBookingResponse(
        row.id(),
        row.reference(),
        row.eventId(),
        row.eventName(),
        row.customerId(),
        row.customerEmail(),
        row.status(),
        row.totalAmount(),
        row.currency(),
        eventSeatIds,
        row.createdAt());
  }
}
