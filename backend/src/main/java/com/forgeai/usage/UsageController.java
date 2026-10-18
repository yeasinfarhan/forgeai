package com.forgeai.usage;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forgeai.common.security.CurrentUser;
import com.forgeai.usage.UsageDtos.UsageSummary;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final AiUsageService usage;

    public UsageController(AiUsageService usage) {
        this.usage = usage;
    }

    /** The signed-in user's AI usage and limits. */
    @GetMapping
    public UsageSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return usage.summary(CurrentUser.id(jwt));
    }
}
