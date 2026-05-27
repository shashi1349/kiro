package com.shashikiranreddy.splitwise.auth.api;

import com.shashikiranreddy.splitwise.auth.application.AuthService;
import com.shashikiranreddy.splitwise.auth.application.AuthService.AuthResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints: register a new user, log in an existing one.
 * Both responses include a bearer JWT the client must send in subsequent calls.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record AuthResponse(Long userId, String email, String name,
                               String tokenType, String accessToken, long expiresInMinutes) {
        static AuthResponse from(AuthResult r) {
            return new AuthResponse(r.userId(), r.email(), r.name(),
                    "Bearer", r.accessToken(), r.expiresInMinutes());
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AuthResponse.from(authService.register(request.email(), request.name(), request.password()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(authService.login(request.email(), request.password()));
    }
}
