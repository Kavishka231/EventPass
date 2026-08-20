package com.eventpass.venue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {
  private final VenueService service;

  public VenueController(VenueService service) {
    this.service = service;
  }

  public record VenueRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 500) String address,
      @NotBlank @Size(max = 120) String city,
      @Positive int capacity) {}

  public record VenueResponse(UUID id, String name, String address, String city, int capacity) {}

  @GetMapping
  public Page<VenueResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return service.list(pageable);
  }

  @GetMapping("/{id}")
  public VenueResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public VenueResponse update(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
