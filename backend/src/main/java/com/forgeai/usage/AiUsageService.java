package com.forgeai.usage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.AiUsageGate;
import com.forgeai.common.exception.TooManyRequestsException;
import com.forgeai.project.ProjectResponse;
import com.forgeai.project.ProjectService;
import com.forgeai.usage.UsageDtos.DayUsage;
import com.forgeai.usage.UsageDtos.Limits;
import com.forgeai.usage.UsageDtos.ProjectUsage;
import com.forgeai.usage.UsageDtos.PurposeUsage;
import com.forgeai.usage.UsageDtos.Totals;
import com.forgeai.usage.UsageDtos.UsageSummary;

/**
 * Records every AI call and enforces the per-user allowance: at most {@code requests-per-hour}
 * calls in any rolling hour and {@code tokens-per-day} tokens in any rolling 24 hours. Limits are
 * checked before each call, so the call that crosses the token limit still completes.
 */
@Service
public class AiUsageService implements AiUsageGate {

    static final Duration HOUR = Duration.ofHours(1);
    static final Duration DAY = Duration.ofHours(24);
    private static final int SUMMARY_DAYS = 14;
    private static final int BREAKDOWN_DAYS = 30;

    private final AiUsageRepository usage;
    private final ProjectService projects;
    private final Limits limits;
    private final Clock clock;

    AiUsageService(
            AiUsageRepository usage,
            ProjectService projects,
            @Value("${forgeai.usage.requests-per-hour:60}") int requestsPerHour,
            @Value("${forgeai.usage.tokens-per-day:2000000}") long tokensPerDay) {
        this.usage = usage;
        this.projects = projects;
        this.limits = new Limits(Math.max(0, requestsPerHour), Math.max(0, tokensPerDay));
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional(readOnly = true)
    public void beforeCall(AiRequest request) {
        AiCaller caller = AiCaller.current();
        if (caller == null) return;
        Instant now = clock.instant();
        if (limits.requestsPerHour() > 0
                && usage.countByUserIdAndCreatedAtAfter(caller.userId(), now.minus(HOUR)) >= limits.requestsPerHour()) {
            Duration wait = untilWindowFrees(caller.userId(), now, HOUR);
            throw new TooManyRequestsException("You have reached the limit of " + limits.requestsPerHour()
                    + " AI requests per hour. Try again in " + TooManyRequestsException.describe(wait) + ".", wait);
        }
        if (limits.tokensPerDay() > 0 && usage.sumTokens(caller.userId(), now.minus(DAY)) >= limits.tokensPerDay()) {
            Duration wait = untilWindowFrees(caller.userId(), now, DAY);
            throw new TooManyRequestsException("You have used your AI allowance of " + String.format("%,d", limits.tokensPerDay())
                    + " tokens per 24 hours. Try again in " + TooManyRequestsException.describe(wait) + ".", wait);
        }
    }

    /** Recorded in its own transaction, so it is kept even when the caller's transaction rolls back. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterCall(AiRequest request, String provider, AiResult result, AiFailure failure, Duration duration) {
        AiCaller caller = AiCaller.current();
        // Without an API key nothing was sent, so nothing is counted.
        if (caller == null || failure == AiFailure.NOT_CONFIGURED) return;
        usage.save(new AiUsage(
                caller.userId(),
                caller.projectId(),
                truncate(request.purpose(), 60),
                truncate(provider, 20),
                result == null ? null : truncate(result.model(), 100),
                result == null ? 0 : result.inputTokens(),
                result == null ? 0 : result.outputTokens(),
                failure == null ? AiUsage.SUCCESS : failure.name(),
                (int) Math.min(Integer.MAX_VALUE, duration.toMillis()),
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public UsageSummary summary(UUID userId) {
        Instant now = clock.instant();
        List<AiUsage> rows = usage.findAllByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, now.minus(Duration.ofDays(BREAKDOWN_DAYS)));

        Totals lastHour = totals(rows.stream().filter(r -> r.getCreatedAt().isAfter(now.minus(HOUR))).toList());
        Totals lastDay = totals(rows.stream().filter(r -> r.getCreatedAt().isAfter(now.minus(DAY))).toList());

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Map<LocalDate, List<AiUsage>> byDay = rows.stream()
                .collect(Collectors.groupingBy(r -> LocalDate.ofInstant(r.getCreatedAt(), ZoneOffset.UTC)));
        List<DayUsage> days = new ArrayList<>();
        for (int i = SUMMARY_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            days.add(new DayUsage(date, totals(byDay.getOrDefault(date, List.of()))));
        }

        List<PurposeUsage> purposes = group(rows, AiUsage::getPurpose).entrySet().stream()
                .map(e -> new PurposeUsage(e.getKey(), totals(e.getValue())))
                .sorted(Comparator.comparingLong((PurposeUsage p) -> -(p.totals().inputTokens() + p.totals().outputTokens())))
                .toList();

        Map<UUID, String> names = projects.list(userId).stream()
                .collect(Collectors.toMap(ProjectResponse::id, ProjectResponse::name));
        List<ProjectUsage> byProject = group(rows.stream().filter(r -> r.getProjectId() != null).toList(), AiUsage::getProjectId)
                .entrySet().stream()
                .map(e -> new ProjectUsage(e.getKey(), names.get(e.getKey()), totals(e.getValue())))
                .sorted(Comparator.comparingLong((ProjectUsage p) -> -(p.totals().inputTokens() + p.totals().outputTokens())))
                .toList();

        return new UsageSummary(limits, lastHour, lastDay, days, purposes, byProject, now);
    }

    // --- Helpers -------------------------------------------------------------------

    /** How long until the oldest call in the window leaves it. */
    private Duration untilWindowFrees(UUID userId, Instant now, Duration window) {
        return usage.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, now.minus(window))
                .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(window)))
                .filter(wait -> !wait.isNegative())
                .orElse(Duration.ofMinutes(1));
    }

    private static <K> Map<K, List<AiUsage>> group(List<AiUsage> rows, Function<AiUsage, K> key) {
        Map<K, List<AiUsage>> groups = new LinkedHashMap<>();
        for (AiUsage row : rows) groups.computeIfAbsent(key.apply(row), k -> new ArrayList<>()).add(row);
        return groups;
    }

    private static Totals totals(List<AiUsage> rows) {
        int failed = 0;
        long input = 0;
        long output = 0;
        for (AiUsage row : rows) {
            if (!AiUsage.SUCCESS.equals(row.getOutcome())) failed++;
            input += row.getInputTokens();
            output += row.getOutputTokens();
        }
        return new Totals(rows.size(), failed, input, output);
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
