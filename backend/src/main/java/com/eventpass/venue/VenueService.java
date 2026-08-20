package com.eventpass.venue;

import com.eventpass.common.error.ApiException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {
  private final VenueRepository venues;

  public VenueService(VenueRepository venues) {
    this.venues = venues;
  }

  @Transactional(readOnly = true)
  public Page<VenueController.VenueResponse> list(Pageable pageable) {
    return venues.findAll(pageable).map(this::response);
  }

  @Transactional(readOnly = true)
  public VenueController.VenueResponse get(UUID id) {
    return response(require(id));
  }

  @Transactional
  public VenueController.VenueResponse create(VenueController.VenueRequest request) {
    return response(venues.save(apply(new Venue(), request)));
  }

  @Transactional
  public VenueController.VenueResponse update(UUID id, VenueController.VenueRequest request) {
    return response(apply(require(id), request));
  }

  @Transactional
  public void delete(UUID id) {
    venues.delete(require(id));
  }

  private Venue require(UUID id) {
    return venues
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "Venue was not found."));
  }

  private Venue apply(Venue venue, VenueController.VenueRequest request) {
    venue.setName(request.name().trim());
    venue.setAddress(request.address().trim());
    venue.setCity(request.city().trim());
    venue.setCapacity(request.capacity());
    return venue;
  }

  private VenueController.VenueResponse response(Venue venue) {
    return new VenueController.VenueResponse(
        venue.getId(), venue.getName(), venue.getAddress(), venue.getCity(), venue.getCapacity());
  }
}
