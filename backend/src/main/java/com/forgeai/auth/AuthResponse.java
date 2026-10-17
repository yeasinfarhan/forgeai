package com.forgeai.auth;

import java.time.Instant;

import com.forgeai.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

    static AuthResponse bearer(IssuedToken token, UserResponse user) {
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), user);
    }
}
