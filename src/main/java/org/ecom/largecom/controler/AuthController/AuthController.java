package org.ecom.largecom.controler.AuthController;

import jakarta.validation.Valid;
import org.ecom.largecom.dto.AuthResponse;
import org.ecom.largecom.dto.LoginRequest;
import org.ecom.largecom.dto.RegisterRequest;
import org.ecom.largecom.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 1. REGISTER
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.resgister(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. LOGIN (This was missing!)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // 3. ME (Protected endpoint)
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of(
                "userId", jwt.getSubject(),
                "tenantId", jwt.getClaim("tenant_id"),
                "email", jwt.getClaim("email"),
                "role", jwt.getClaim("role")
        ));
    }
}
