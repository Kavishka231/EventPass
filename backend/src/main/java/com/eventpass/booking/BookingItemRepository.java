package com.eventpass.booking;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {
  @Query(
      """
      select new com.eventpass.booking.BookingSeatRow(item.booking.id, item.eventSeat.id)
      from BookingItem item
      where item.booking.id in :bookingIds
      order by item.booking.id, item.id
      """)
  List<BookingSeatRow> findSeatRowsByBookingIds(@Param("bookingIds") Collection<UUID> bookingIds);
}
