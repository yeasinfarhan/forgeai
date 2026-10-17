package com.forgeai.health;

public record HealthResponse(String status, String service) {

    public static HealthResponse up(String service) {
        return new HealthResponse("UP", service);
    }
}
