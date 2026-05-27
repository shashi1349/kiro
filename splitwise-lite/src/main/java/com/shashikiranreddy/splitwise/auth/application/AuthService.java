package com.shashikiranreddy.splitwise.auth.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ConflictException;
import com.shashikiranreddy.splitwise.common.security.JwtService;
import com.shashikiranreddy.splitwise.user.domain.User;
import com.shashikiranreddy.splitwise.user.domain.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login. Passwords are stored only as BCrypt hashes;
 * successful authentication produces a signed JWT for stateless authorization.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record AuthResult(Long userId, String email, String name,
                             String accessToken, long expiresInMinutes) {}

    @Transactional
    public AuthResult register(String email, String name, String rawPassword) {
        String normalized = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("A user with this email already exists.");
        }
        User user = new User(normalized, name.trim(), passwordEncoder.encode(rawPassword));
        user = userRepository.save(user);
        return toResult(user);
    }

    @Transactional(readOnly = true)
    public AuthResult login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return toResult(user);
    }

    private AuthResult toResult(User user) {
        String token = jwtService.issueToken(user.getId(), user.getEmail());
        return new AuthResult(user.getId(), user.getEmail(), user.getName(),
                token, jwtService.getTtlMinutes());
    }
}
