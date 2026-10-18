package com.forgeai.usage;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.forgeai.common.security.CurrentUser;

/**
 * Who an AI call is made for: the signed-in user of the current request and, for project
 * endpoints ({@code /api/projects/{id}/...}), the project. AI calls run on the request thread.
 *
 * @param projectId null outside project endpoints
 */
record AiCaller(UUID userId, UUID projectId) {

    private static final Pattern PROJECT_PATH = Pattern.compile("^/api/projects/([0-9a-fA-F-]{36})(/|$)");

    /** The current caller, or null when there is no signed-in user (no limits apply then). */
    static AiCaller current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) return null;
        UUID userId;
        try {
            userId = CurrentUser.id(token.getToken());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        UUID projectId = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Matcher matcher = PROJECT_PATH.matcher(attributes.getRequest().getRequestURI());
            if (matcher.find()) {
                try {
                    projectId = UUID.fromString(matcher.group(1));
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID: the call is recorded without a project.
                }
            }
        }
        return new AiCaller(userId, projectId);
    }
}
