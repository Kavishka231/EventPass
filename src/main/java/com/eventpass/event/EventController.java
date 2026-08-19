package com.eventpass.event;

import com.eventpass.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/events")
public class EventController {
  private final EventService service; public EventController(EventService service){this.service=service;}
  public record EventRequest(@NotBlank @Size(max=250) String name,@NotBlank @Size(max=4000) String description,@NotBlank String category,@NotNull @Future Instant startDateTime,@NotNull @Future Instant endDateTime,@NotNull UUID venueId,@NotNull Event.Status status){}
  public record EventResponse(UUID id,String name,String description,String category,Instant startDateTime,Instant endDateTime,Event.Status status,UUID venueId,String venueName,String city){}
  @GetMapping Page<EventResponse> list(@RequestParam(required=false)String category,@RequestParam(required=false)String city,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant startDate,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant endDate,@RequestParam(required=false)Event.Status status,@PageableDefault(size=20,sort="startDateTime")Pageable pageable){return service.search(category,city,startDate,endDate,status,pageable);}
  @GetMapping("/{id}") EventResponse get(@PathVariable UUID id){return service.get(id);}
  @PostMapping @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')") ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest r,@AuthenticationPrincipal User u){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r,u));}
  @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')") EventResponse update(@PathVariable UUID id,@Valid @RequestBody EventRequest r,@AuthenticationPrincipal User u){return service.update(id,r,u);}
  @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')") ResponseEntity<Void> cancel(@PathVariable UUID id,@AuthenticationPrincipal User u){service.cancel(id,u);return ResponseEntity.noContent().build();}
}
