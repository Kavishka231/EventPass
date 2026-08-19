package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.event.*;
import com.eventpass.payment.*;
import com.eventpass.seat.*;
import com.eventpass.ticket.*;
import com.eventpass.user.User;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private final BookingRepository bookings;private final EventRepository events;private final EventSeatRepository seats;private final SeatLockService locks;private final PaymentProvider payments;private final PaymentRepository paymentRepo;private final TicketRepository tickets;private final Duration holdTtl;private final SecureRandom random=new SecureRandom();
  public BookingService(BookingRepository bookings,EventRepository events,EventSeatRepository seats,SeatLockService locks,PaymentProvider payments,PaymentRepository paymentRepo,TicketRepository tickets,@Value("${eventpass.booking.hold-ttl}")Duration holdTtl){this.bookings=bookings;this.events=events;this.seats=seats;this.locks=locks;this.payments=payments;this.paymentRepo=paymentRepo;this.tickets=tickets;this.holdTtl=holdTtl;}
  @Transactional public BookingController.BookingResponse create(BookingController.CreateBookingRequest r,String key,User user){
    var existing=bookings.findByIdempotencyKey(key);if(existing.isPresent()){if(!existing.get().getUser().getId().equals(user.getId()))throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency key belongs to another request.");return response(existing.get());}
    Event event=events.findById(r.eventId()).filter(e->e.getStatus()==Event.Status.PUBLISHED&&e.getStartDateTime().isAfter(Instant.now())).orElseThrow(()->new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"EVENT_NOT_BOOKABLE","Event is not available for booking."));
    List<UUID> requested=r.eventSeatIds().stream().distinct().sorted().toList();if(requested.size()!=r.eventSeatIds().size())throw new ApiException(HttpStatus.BAD_REQUEST,"DUPLICATE_SEAT","A seat was selected more than once.");String owner=UUID.randomUUID().toString();List<UUID> acquired=new ArrayList<>();
    try{for(UUID id:requested){if(!locks.acquire(event.getId(),id,owner))throw new ApiException(HttpStatus.CONFLICT,"SEAT_UNAVAILABLE","One or more selected seats are currently held.");acquired.add(id);}List<EventSeat> inventory=seats.lockForBooking(event.getId(),requested);if(inventory.size()!=requested.size()||inventory.stream().anyMatch(s->s.getStatus()!=EventSeat.Status.AVAILABLE))throw new ApiException(HttpStatus.CONFLICT,"SEAT_UNAVAILABLE","One or more selected seats are no longer available.");
      Booking b=new Booking();b.setBookingReference("EVP-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());b.setUser(user);b.setEvent(event);b.setCurrency("LKR");b.setExpiresAt(Instant.now().plus(holdTtl));b.setIdempotencyKey(key);b.setTotalAmount(inventory.stream().map(EventSeat::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add));inventory.forEach(s->{s.setStatus(EventSeat.Status.HELD);BookingItem i=new BookingItem();i.setBooking(b);i.setEventSeat(s);i.setUnitPrice(s.getPrice());b.getItems().add(i);});bookings.save(b);
      var result=payments.charge(r.paymentToken(),b.getTotalAmount(),b.getCurrency(),key);Payment p=new Payment();p.setBooking(b);p.setPaymentReference(result.reference());p.setAmount(b.getTotalAmount());p.setCurrency(b.getCurrency());p.setProvider("MOCK");p.setStatus(result.successful()?Payment.Status.SUCCESS:Payment.Status.FAILED);paymentRepo.save(p);if(!result.successful()){b.setStatus(Booking.Status.FAILED);inventory.forEach(s->s.setStatus(EventSeat.Status.AVAILABLE));throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PAYMENT_FAILED","Mock payment was declined.");}
      b.setStatus(Booking.Status.CONFIRMED);inventory.forEach(s->{s.setStatus(EventSeat.Status.SOLD);Ticket t=new Ticket();t.setTicketNumber("TKT-"+UUID.randomUUID());t.setBooking(b);t.setEventSeat(s);byte[] token=new byte[32];random.nextBytes(token);t.setQrToken(Base64.getUrlEncoder().withoutPadding().encodeToString(token));t.setIssuedAt(Instant.now());tickets.save(t);});return response(b);
    }finally{acquired.forEach(id->locks.release(event.getId(),id,owner));}
  }
  @Transactional(readOnly=true) public List<BookingController.BookingResponse> list(User u){return bookings.findAllByUserIdOrderByCreatedAtDesc(u.getId()).stream().map(this::response).toList();}
  @Transactional(readOnly=true) public BookingController.BookingResponse get(UUID id,User u){return response(owned(id,u));}
  @Transactional public void cancel(UUID id,User u){Booking b=owned(id,u);if(b.getStatus()!=Booking.Status.CONFIRMED||!b.getEvent().getStartDateTime().isAfter(Instant.now().plus(Duration.ofHours(24))))throw new ApiException(HttpStatus.CONFLICT,"BOOKING_NOT_CANCELLABLE","Booking is no longer eligible for cancellation.");b.setStatus(Booking.Status.CANCELLED);b.getItems().forEach(i->i.getEventSeat().setStatus(EventSeat.Status.AVAILABLE));tickets.findAllByBookingUserId(u.getId()).stream().filter(t->t.getBooking().getId().equals(id)).forEach(t->t.setStatus(Ticket.Status.CANCELLED));}
  private Booking owned(UUID id,User u){return bookings.findById(id).filter(b->u.getRole()==User.Role.ADMIN||b.getUser().getId().equals(u.getId())).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"BOOKING_NOT_FOUND","Booking was not found."));}
  private BookingController.BookingResponse response(Booking b){return new BookingController.BookingResponse(b.getId(),b.getBookingReference(),b.getEvent().getId(),b.getStatus(),b.getTotalAmount(),b.getCurrency(),b.getItems().stream().map(i->i.getEventSeat().getId()).toList(),b.getCreatedAt());}
}
