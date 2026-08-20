package com.eventpass.seat;

import com.eventpass.common.error.ApiException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/events/{eventId}/seats")
public class SeatController {
  private final EventSeatRepository seats; public SeatController(EventSeatRepository seats){this.seats=seats;}
  public record SeatResponse(UUID id,String section,String row,String number,Seat.Type type,BigDecimal price,EventSeat.Status availability){}
  @GetMapping @Transactional(readOnly=true) List<SeatResponse> list(@PathVariable UUID eventId){List<EventSeat> found=seats.findAllByEventId(eventId);if(found.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"SEATS_NOT_FOUND","No seat inventory exists for this event.");return found.stream().map(e->new SeatResponse(e.getId(),e.getSeat().getSection(),e.getSeat().getRowNumber(),e.getSeat().getSeatNumber(),e.getSeat().getSeatType(),e.getPrice(),e.getStatus())).toList();}
}
