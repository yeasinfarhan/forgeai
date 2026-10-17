import type { ReactNode } from "react";

import type { ProjectBrief } from "@/lib/api";

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-2">
      <h3 className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">{title}</h3>
      {children}
    </section>
  );
}

function BulletList({ items, empty }: { items: string[]; empty: string }) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground italic">{empty}</p>;
  }
  return (
    <ul className="list-disc space-y-1 pl-5 text-sm marker:text-muted-foreground">
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ul>
  );
}

/** Read-only rendering of the brief Claude wrote at the end of idea intake. */
export function ProjectBriefView({ brief }: { brief: ProjectBrief }) {
  return (
    <article className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-lg font-semibold tracking-tight">{brief.title}</h2>
        <p className="text-sm leading-relaxed">{brief.summary}</p>
      </header>

      {brief.problem && (
        <Section title="Problem">
          <p className="text-sm leading-relaxed">{brief.problem}</p>
        </Section>
      )}

      <div className="grid gap-6 sm:grid-cols-2">
        <Section title="Target users">
          <BulletList items={brief.targetUsers} empty="Not specified." />
        </Section>
        <Section title="Goals">
          <BulletList items={brief.goals} empty="Not specified." />
        </Section>
      </div>

      <Section title="Core features">
        <ul className="grid gap-3 sm:grid-cols-2">
          {brief.coreFeatures.map((feature) => (
            <li key={feature.name} className="rounded-lg border bg-background/40 p-3">
              <p className="text-sm font-medium">{feature.name}</p>
              <p className="mt-1 text-sm text-muted-foreground">{feature.description}</p>
            </li>
          ))}
        </ul>
      </Section>

      <div className="grid gap-6 sm:grid-cols-2">
        <Section title="Out of scope">
          <BulletList items={brief.outOfScope} empty="Nothing excluded yet." />
        </Section>
        <Section title="Constraints">
          <BulletList items={brief.constraints} empty="None stated." />
        </Section>
      </div>

      <Section title="Open questions">
        <BulletList items={brief.openQuestions} empty="None — the brief covers everything discussed." />
      </Section>
    </article>
  );
}
