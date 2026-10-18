package com.forgeai.common.concurrent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.forgeai.common.exception.ConflictException;

/**
 * Lets only one AI generation run per project and stage at a time. AI calls can take minutes;
 * without this, a second click (for example after the browser gave up waiting) starts another run
 * that then collides with the first. Held in memory, which matches the single backend instance
 * ForgeAI runs as.
 */
@Component
public class GenerationGuard {

    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * Runs {@code action} unless the same stage of the project is already being generated.
     *
     * @param what lower-case name for the message, e.g. "API design"
     */
    public <T> T run(UUID projectId, String stage, String what, Supplier<T> action) {
        String key = projectId + "/" + stage;
        if (!running.add(key)) {
            throw new ConflictException("The " + what + " is already being generated. Wait until it finishes, then reload the page.");
        }
        try {
            return action.get();
        } finally {
            running.remove(key);
        }
    }
}
