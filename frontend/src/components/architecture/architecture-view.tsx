import type { ReactNode } from "react";

import { componentDiagram } from "@/components/architecture/component-diagram";
import { MermaidDiagram } from "@/components/architecture/mermaid-diagram";
import { Badge } from "@/components/ui/badge";
import type { ArchitectureContent, ComponentKind, RequirementRef } from "@/lib/api";
import { cn } from "@/lib/utils";

export const KIND_LABEL: Record<ComponentKind, string> = {
  CLIENT: "Client",
  SERVICE: "Service",
  DATA_STORE: "Data store",
  EXTERNAL: "External system",
  INFRASTRUCTURE: "Infrastructure",
};

export function decisionCode(index: number) {
  return `ADR-${String(index + 1).padStart(3, "0")}`;
}

/** A titled block of the architecture page; `action` holds e.g. the Edit button. */
export function Section({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="space-y-4 rounded-xl border bg-card p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold">{title}</h2>
          {description && <p className="mt-0.5 text-sm text-muted-foreground">{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

function RequirementChips({ codes, requirements }: { codes: string[]; requirements: RequirementRef[] }) {
  if (codes.length === 0) return null;
  const titles = new Map(requirements.map((r) => [r.code, r.title]));
  return (
    <div className="flex flex-wrap gap-1">
      {codes.map((code) => (
        <Badge key={code} variant="outline" className="font-mono" title={titles.get(code) ?? "Not an approved requirement"}>
          {code}
        </Badge>
      ))}
    </div>
  );
}

function Labeled({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-0.5 text-xs font-semibold tracking-wider text-muted-foreground uppercase">{label}</p>
      <div className="text-sm leading-relaxed">{children}</div>
    </div>
  );
}

export function OverviewSection({ content, action }: { content: ArchitectureContent; action?: ReactNode }) {
  return (
    <Section title="Overview" action={action}>
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="secondary">{content.style}</Badge>
      </div>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{content.overview}</p>
      <Labeled label="Why this style">
        <p className="whitespace-pre-wrap text-muted-foreground">{content.styleRationale}</p>
      </Labeled>
    </Section>
  );
}

export function ComponentsSection({
  content,
  requirements,
  action,
}: {
  content: ArchitectureContent;
  requirements: RequirementRef[];
  action?: ReactNode;
}) {
  return (
    <Section
      title="Components"
      description="The building blocks and how they depend on each other. The diagram is drawn from this list."
      action={action}
    >
      <MermaidDiagram source={componentDiagram(content.components)} label="Component diagram" />
      <ul className="grid gap-3 sm:grid-cols-2">
        {content.components.map((component) => (
          <li key={component.name} className="space-y-2 rounded-lg border bg-background/40 p-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium">{component.name}</span>
              <Badge variant="outline">{KIND_LABEL[component.kind]}</Badge>
            </div>
            {component.technology && <p className="font-mono text-xs text-primary">{component.technology}</p>}
            <p className="text-sm text-muted-foreground">{component.responsibility}</p>
            {component.dependsOn.length > 0 && (
              <p className="text-xs text-muted-foreground">
                Depends on: <span className="text-foreground">{component.dependsOn.join(", ")}</span>
              </p>
            )}
            <RequirementChips codes={component.requirements} requirements={requirements} />
          </li>
        ))}
      </ul>
    </Section>
  );
}

export function DecisionsSection({
  content,
  requirements,
  action,
}: {
  content: ArchitectureContent;
  requirements: RequirementRef[];
  action?: ReactNode;
}) {
  return (
    <Section
      title="Decisions"
      description="Architecture decision records: technology choices, alternatives and trade-offs."
      action={action}
    >
      {content.decisions.length === 0 ? (
        <p className="text-sm text-muted-foreground italic">No decisions recorded.</p>
      ) : (
        <ul className="space-y-2">
          {content.decisions.map((decision, index) => (
            <li key={index}>
              <details className="group rounded-lg border bg-background/40 open:bg-background/60">
                <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2 p-4">
                  <span className="font-mono text-xs text-muted-foreground">{decisionCode(index)}</span>
                  <span className="font-medium">{decision.title}</span>
                  <span className="ml-auto text-xs text-muted-foreground group-open:hidden">Show</span>
                  <span className="ml-auto hidden text-xs text-muted-foreground group-open:inline">Hide</span>
                </summary>
                <div className="space-y-3 border-t px-4 pt-3 pb-4">
                  <Labeled label="Context">
                    <p className="whitespace-pre-wrap">{decision.context}</p>
                  </Labeled>
                  <Labeled label="Decision">
                    <p className="whitespace-pre-wrap">{decision.decision}</p>
                  </Labeled>
                  {decision.alternatives.length > 0 && (
                    <Labeled label="Alternatives considered">
                      <ul className="list-disc space-y-0.5 pl-5 marker:text-muted-foreground">
                        {decision.alternatives.map((alternative, i) => (
                          <li key={i}>{alternative}</li>
                        ))}
                      </ul>
                    </Labeled>
                  )}
                  <Labeled label="Consequences and trade-offs">
                    <p className="whitespace-pre-wrap">{decision.consequences}</p>
                  </Labeled>
                  <RequirementChips codes={decision.requirements} requirements={requirements} />
                </div>
              </details>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

export function FlowsSection({ content, action }: { content: ArchitectureContent; action?: ReactNode }) {
  return (
    <Section title="Key flows" description="How the components work together in the most important interactions." action={action}>
      {content.flows.length === 0 ? (
        <p className="text-sm text-muted-foreground italic">No flows described.</p>
      ) : (
        <div className="space-y-5">
          {content.flows.map((flow, index) => (
            <div key={index} className="space-y-2">
              <h3 className="text-sm font-medium">{flow.title}</h3>
              {flow.description && <p className="text-sm text-muted-foreground">{flow.description}</p>}
              <MermaidDiagram source={flow.diagram} label={`Sequence diagram: ${flow.title}`} />
              <details className="text-xs">
                <summary className="cursor-pointer text-muted-foreground hover:text-foreground">Mermaid source</summary>
                <pre className="mt-2 overflow-x-auto rounded-md border bg-background/60 p-3 font-mono">{flow.diagram}</pre>
              </details>
            </div>
          ))}
        </div>
      )}
    </Section>
  );
}

/** Which component or decision addresses each approved requirement. */
export function CoverageSection({ content, requirements }: { content: ArchitectureContent; requirements: RequirementRef[] }) {
  if (requirements.length === 0) return null;
  const addressedBy = (code: string) => [
    ...content.components.filter((c) => c.requirements.includes(code)).map((c) => c.name),
    ...content.decisions.flatMap((d, i) => (d.requirements.includes(code) ? [decisionCode(i)] : [])),
  ];
  return (
    <Section title="Requirement coverage" description="Every approved requirement should be addressed by a component or a decision.">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="py-2 pr-3 font-medium">Requirement</th>
              <th className="py-2 font-medium">Addressed by</th>
            </tr>
          </thead>
          <tbody>
            {requirements.map((requirement) => {
              const by = addressedBy(requirement.code);
              return (
                <tr key={requirement.code} className="border-b last:border-0 align-top">
                  <td className="py-2 pr-3">
                    <span className="mr-2 font-mono text-xs text-muted-foreground">{requirement.code}</span>
                    {requirement.title}
                  </td>
                  <td className={cn("py-2", by.length === 0 && "text-destructive")}>
                    {by.length === 0 ? "Not addressed" : by.join(", ")}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Section>
  );
}

/** The whole architecture, read-only (used for earlier revisions). */
export function ArchitectureView({ content, requirements }: { content: ArchitectureContent; requirements: RequirementRef[] }) {
  return (
    <div className="space-y-4">
      <OverviewSection content={content} />
      <ComponentsSection content={content} requirements={requirements} />
      <DecisionsSection content={content} requirements={requirements} />
      <FlowsSection content={content} />
    </div>
  );
}
