package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.event.Event;
import com.eventpass.event.EventRepository;
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
public class OrganizerBookingService {
  private final EventRepository events;
  private final BookingRepository bookings;
  private final BookingItemRepository bookingItems;

  public OrganizerBookingService(
      EventRepository events, BookingRepository bookings, BookingItemRepository bookingItems) {
    this.events = events;
    this.bookings = bookings;
    this.bookingItems = bookingItems;
  }

  @Transactional(readOnly = true)
  public Page<OrganizerBookingController.EventBookingResponse> report(
      UUID eventId, User organizer, Pageable pageable) {
    Event event =
        events
            .findById(eventId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found."));
    if (!event.getOrganizer().getId().equals(organizer.getId())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "EVENT_ACCESS_DENIED",
          "You are not authorized to view bookings for this event.");
    }
    Page<EventBookingReportRow> page = bookings.findReportRowsByEventId(eventId, pageable);
    List<UUID> bookingIds = page.getContent().stream().map(EventBookingReportRow::id).toList();
    Map<UUID, List<UUID>> seatsByBooking = seatsByBooking(bookingIds);
    return page.map(
        row -> response(row, eventId, seatsByBooking.getOrDefault(row.id(), List.of())));
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

  private OrganizerBookingController.EventBookingResponse response(
      EventBookingReportRow row, UUID eventId, List<UUID> eventSeatIds) {
    return new OrganizerBookingController.EventBookingResponse(
        row.id(),
        row.reference(),
        eventId,
        row.customerId(),
        row.customerEmail(),
        row.customerFirstName(),
        row.customerLastName(),
        row.status(),
        row.totalAmount(),
        row.currency(),
        eventSeatIds,
        row.createdAt());
  }
}
