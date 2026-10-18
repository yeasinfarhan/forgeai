import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router";

import { FormError } from "@/components/form-field";
import { errorMessage, getUsage, isAbortError, type UsageSummary, type UsageTotals } from "@/lib/api";
import { formatDateTime } from "@/lib/format";

/** Readable names for the kinds of AI calls (the backend's request purposes). */
const PURPOSE: Record<string, string> = {
  "idea-intake.turn": "Idea intake: questions",
  "idea-intake.brief": "Idea intake: brief",
  "requirements.generate": "Requirements",
  "architecture.generate": "Architecture",
  "database-design.generate": "Database design",
  "api-design.generate": "API design",
  "tasks.generate": "Development tasks",
  "code.generate": "Code",
  "tests.generate": "Tests",
  "review.generate": "Code review",
  "docs.readme": "README",
  "docs.developer_guide": "Developer guide",
};

const number = new Intl.NumberFormat();

function tokens(totals: UsageTotals) {
  return totals.inputTokens + totals.outputTokens;
}

export function UsagePage() {
  const [usage, setUsage] = useState<UsageSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getUsage(controller.signal)
      .then(setUsage)
      .catch((err: unknown) => {
        if (!isAbortError(err)) setError(errorMessage(err));
      });
    return () => controller.abort();
  }, []);

  if (error) return <FormError message={error} />;
  if (!usage) return <div aria-hidden="true" className="h-96 animate-pulse rounded-xl border bg-card" />;

  const { limits } = usage;

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-semibold tracking-tight">AI usage</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your AI calls and the tokens the provider billed for them. Limits apply to each user over a rolling window and
          are set by the administrator (AI_REQUESTS_PER_HOUR, AI_TOKENS_PER_DAY).
        </p>
      </section>

      <section aria-label="Current allowance" className="grid gap-4 sm:grid-cols-2">
        <Allowance
          title="Requests in the last hour"
          used={usage.lastHour.requests}
          limit={limits.requestsPerHour}
          detail={usage.lastHour.failed > 0 ? `${usage.lastHour.failed} failed` : null}
        />
        <Allowance
          title="Tokens in the last 24 hours"
          used={tokens(usage.last24Hours)}
          limit={limits.tokensPerDay}
          detail={`${number.format(usage.last24Hours.inputTokens)} in · ${number.format(usage.last24Hours.outputTokens)} out`}
        />
      </section>

      <section className="space-y-3 rounded-xl border bg-card p-5">
        <h2 className="font-semibold">Last 14 days</h2>
        <UsageTable
          caption="AI usage per day (UTC), newest first"
          head="Day"
          rows={[...usage.days].reverse().map((day) => ({ key: day.date, label: day.date, totals: day.totals }))}
        />
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="space-y-3 rounded-xl border bg-card p-5">
          <h2 className="font-semibold">By kind, last 30 days</h2>
          {usage.purposes.length === 0 ? (
            <p className="text-sm text-muted-foreground">No AI calls in the last 30 days.</p>
          ) : (
            <UsageTable
              caption="AI usage by kind of call"
              head="Kind"
              rows={usage.purposes.map((p) => ({ key: p.purpose, label: PURPOSE[p.purpose] ?? p.purpose, totals: p.totals }))}
            />
          )}
        </section>
        <section className="space-y-3 rounded-xl border bg-card p-5">
          <h2 className="font-semibold">By project, last 30 days</h2>
          {usage.projects.length === 0 ? (
            <p className="text-sm text-muted-foreground">No AI calls in the last 30 days.</p>
          ) : (
            <UsageTable
              caption="AI usage by project"
              head="Project"
              rows={usage.projects.map((p) => ({
                key: p.projectId,
                label: p.projectName ? (
                  <Link to={`/projects/${p.projectId}`} className="text-primary hover:underline">
                    {p.projectName}
                  </Link>
                ) : (
                  <span className="text-muted-foreground">Deleted project</span>
                ),
                totals: p.totals,
              }))}
            />
          )}
        </section>
      </div>

      <p className="text-xs text-muted-foreground">As of {formatDateTime(usage.generatedAt)}.</p>
    </div>
  );
}

function Allowance({ title, used, limit, detail }: { title: string; used: number; limit: number; detail: string | null }) {
  const reached = limit > 0 && used >= limit;
  return (
    <div className="space-y-1 rounded-xl border bg-card p-5">
      <h2 className="text-sm text-muted-foreground">{title}</h2>
      <p className="text-2xl font-semibold tabular-nums">
        {number.format(used)}
        <span className="text-base font-normal text-muted-foreground">
          {limit > 0 ? ` of ${number.format(limit)}` : " (no limit)"}
        </span>
      </p>
      {reached && <p className="text-sm text-destructive">Limit reached: new AI requests are refused until older ones leave the window.</p>}
      {detail && <p className="text-xs text-muted-foreground">{detail}</p>}
    </div>
  );
}

function UsageTable({
  caption,
  head,
  rows,
}: {
  caption: string;
  head: string;
  rows: { key: string; label: ReactNode; totals: UsageTotals }[];
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b text-left text-xs text-muted-foreground">
            <th scope="col" className="py-2 pr-3 font-medium">
              {head}
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">
              Requests
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">
              Failed
            </th>
            <th scope="col" className="py-2 text-right font-medium">
              Tokens
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} className="border-b last:border-0">
              <th scope="row" className="py-1.5 pr-3 text-left font-normal">
                {row.label}
              </th>
              <td className="py-1.5 pr-3 text-right tabular-nums">{number.format(row.totals.requests)}</td>
              <td className="py-1.5 pr-3 text-right tabular-nums text-muted-foreground">{number.format(row.totals.failed)}</td>
              <td className="py-1.5 text-right tabular-nums">{number.format(tokens(row.totals))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
