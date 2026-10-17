package com.forgeai.health;

import org.springframework.stereotype.Service;

/**
 * Reports whether the API process is up and able to serve requests.
 * Database connectivity is verified separately at startup by
 * {@link com.forgeai.config.DatabaseConnectionVerifier}.
 */
@Service
public class HealthService {

    static final String SERVICE_NAME = "ForgeAI";

    public HealthResponse currentHealth() {
        return HealthResponse.up(SERVICE_NAME);
    }
}
