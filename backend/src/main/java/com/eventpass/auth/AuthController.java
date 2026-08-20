package com.eventpass.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service; public AuthController(AuthService service){this.service=service;}
  public record RegisterRequest(@NotBlank @Email String email,@NotBlank @Size(min=10,max=72) String password,@NotBlank @Size(max=100) String firstName,@NotBlank @Size(max=100) String lastName){}
  public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
  public record TokenRequest(@NotBlank String refreshToken){}
  public record AuthResponse(String accessToken,String refreshToken,String tokenType,String role){}
  @PostMapping("/register") ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.register(r));}
  @PostMapping("/login") AuthResponse login(@Valid @RequestBody LoginRequest r){return service.login(r);}
  @PostMapping("/refresh") AuthResponse refresh(@Valid @RequestBody TokenRequest r){return service.refresh(r.refreshToken());}
  @PostMapping("/logout") ResponseEntity<Void> logout(@Valid @RequestBody TokenRequest r){service.logout(r.refreshToken());return ResponseEntity.noContent().build();}
}
