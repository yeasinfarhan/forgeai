package com.forgeai.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.user.User;
import com.forgeai.user.UserRepository;
import com.forgeai.user.UserResponse;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

    /**
     * A real BCrypt hash, compared against when the email is unknown so that login takes the same time
     * whether or not the account exists (prevents discovering registered emails by timing).
     */
    private final String dummyPasswordHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()), request.displayName().strip());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two registrations for the same email raced past the existence check.
            throw new EmailAlreadyRegisteredException();
        }
        return AuthResponse.bearer(tokens.issueFor(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String password = request.password();
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidCredentialsException();
        }

        User user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(password, hash);

        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return AuthResponse.bearer(tokens.issueFor(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
