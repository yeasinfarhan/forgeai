package com.forgeai.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for signing and validating access tokens, bound from {@code forgeai.security.jwt.*}.
 * Validated at startup so a missing or weak {@code JWT_SECRET} stops the application immediately.
 */
@Validated
@ConfigurationProperties("forgeai.security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET must be set") String secret,
        @NotNull Duration expiration) {

    static final int MIN_SECRET_BYTES = 32;

    @AssertTrue(message = "JWT_SECRET must be at least 32 bytes long")
    public boolean isSecretLongEnough() {
        return secret == null || secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
    }
}
