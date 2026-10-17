package com.forgeai.auth;

import java.time.Instant;

record IssuedToken(String value, Instant expiresAt) {
}
