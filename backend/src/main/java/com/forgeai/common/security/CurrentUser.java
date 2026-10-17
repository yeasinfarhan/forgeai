package com.forgeai.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated user's id from the access token (its subject claim). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
