package com.forgeai.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response bodies of the usage API. */
public final class UsageDtos {

    private UsageDtos() {
    }

    /** The allowance per user; 0 means no limit. */
    public record Limits(int requestsPerHour, long tokensPerDay) {
    }

    /** Totals over a period. */
    public record Totals(int requests, int failed, long inputTokens, long outputTokens) {
    }

    /** One UTC day. */
    public record DayUsage(LocalDate date, Totals totals) {
    }

    public record PurposeUsage(String purpose, Totals totals) {
    }

    /** @param projectName null when the project has been deleted */
    public record ProjectUsage(UUID projectId, String projectName, Totals totals) {
    }

    /**
     * The caller's AI usage.
     *
     * @param lastHour    counted against {@code limits.requestsPerHour}
     * @param last24Hours counted against {@code limits.tokensPerDay}
     * @param days        the last 14 days (UTC), oldest first, including days without calls
     * @param purposes    the last 30 days by kind of call, most tokens first
     * @param projects    the last 30 days by project, most tokens first
     */
    public record UsageSummary(
            Limits limits,
            Totals lastHour,
            Totals last24Hours,
            List<DayUsage> days,
            List<PurposeUsage> purposes,
            List<ProjectUsage> projects,
            Instant generatedAt) {
    }
}
