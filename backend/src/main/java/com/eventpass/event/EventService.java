package com.eventpass.event;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.User;
import com.eventpass.venue.VenueRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private final EventRepository events; private final VenueRepository venues;
  public EventService(EventRepository events,VenueRepository venues){this.events=events;this.venues=venues;}
  @Transactional(readOnly=true) public Page<EventController.EventResponse> search(String category,String city,Instant from,Instant to,Event.Status status,Pageable pageable){
    Specification<Event> s=(r,q,b)->b.conjunction();
    if(category!=null)s=s.and((r,q,b)->b.equal(r.get("category"),category));if(city!=null)s=s.and((r,q,b)->b.equal(r.get("venue").get("city"),city));if(from!=null)s=s.and((r,q,b)->b.greaterThanOrEqualTo(r.get("startDateTime"),from));if(to!=null)s=s.and((r,q,b)->b.lessThanOrEqualTo(r.get("startDateTime"),to));s=s.and((r,q,b)->b.equal(r.get("status"),status==null?Event.Status.PUBLISHED:status));return events.findAll(s,pageable).map(this::response);
  }
  @Transactional(readOnly=true) public EventController.EventResponse get(UUID id){Event e=events.findById(id).filter(x->x.getStatus()==Event.Status.PUBLISHED).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event was not found."));return response(e);}
  @Transactional public EventController.EventResponse create(EventController.EventRequest r,User organizer){Event e=new Event();apply(e,r);e.setOrganizer(organizer);return response(events.save(e));}
  @Transactional public EventController.EventResponse update(UUID id,EventController.EventRequest r,User actor){Event e=owned(id,actor);apply(e,r);return response(e);}
  @Transactional public void cancel(UUID id,User actor){owned(id,actor).setStatus(Event.Status.CANCELLED);}
  private Event owned(UUID id,User u){Event e=events.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event was not found."));if(u.getRole()!=User.Role.ADMIN&&!e.getOrganizer().getId().equals(u.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"EVENT_FORBIDDEN","You cannot manage this event.");return e;}
  private void apply(Event e,EventController.EventRequest r){if(!r.endDateTime().isAfter(r.startDateTime()))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EVENT_DATES","End time must be after start time.");e.setName(r.name());e.setDescription(r.description());e.setCategory(r.category());e.setStartDateTime(r.startDateTime());e.setEndDateTime(r.endDateTime());e.setStatus(r.status());e.setVenue(venues.findById(r.venueId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"VENUE_NOT_FOUND","Venue was not found.")));}
  private EventController.EventResponse response(Event e){return new EventController.EventResponse(e.getId(),e.getName(),e.getDescription(),e.getCategory(),e.getStartDateTime(),e.getEndDateTime(),e.getStatus(),e.getVenue().getId(),e.getVenue().getName(),e.getVenue().getCity());}
}
