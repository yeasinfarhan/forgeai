import { useEffect, useState, type FormEvent } from "react";
import { MessageCircleQuestion, RotateCcw, Sparkles } from "lucide-react";

import { FormError } from "@/components/form-field";
import { ProjectBriefView } from "@/components/intake/project-brief-view";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  answerIntake,
  errorMessage,
  getIntake,
  isAbortError,
  startIntake,
  writeIntakeBrief,
  type Intake,
  type IntakeRound,
  type Project,
} from "@/lib/api";
import { formatDateTime } from "@/lib/format";

const IDEA_MAX = 10_000;
const ANSWER_MAX = 4_000;

type LoadState = { kind: "loading" } | { kind: "loaded"; intake: Intake } | { kind: "error"; message: string };

/**
 * Idea intake for one project: describe the idea → answer Claude's clarifying questions →
 * read the brief. Every step that calls Claude can take a while, so the panel shows progress
 * and keeps the user's input if a call fails.
 */
export function IdeaIntakePanel({ project }: { project: Project }) {
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [busyMessage, setBusyMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getIntake(project.id, controller.signal)
      .then((intake) => setState({ kind: "loaded", intake }))
      .catch((error: unknown) => {
        if (isAbortError(error) || controller.signal.aborted) return;
        setState({ kind: "error", message: errorMessage(error) });
      });
    return () => controller.abort();
  }, [project.id]);

  /** Runs a Claude-backed action; on failure the current screen (and the user's input) stays. */
  async function run(message: string, action: () => Promise<Intake>) {
    setActionError(null);
    setBusyMessage(message);
    try {
      const intake = await action();
      setState({ kind: "loaded", intake });
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusyMessage(null);
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <Sparkles className="size-4 text-primary" />
            Idea intake
          </CardTitle>
          <CardDescription>
            Describe your idea. Claude asks a few clarifying questions, then writes a project brief.
          </CardDescription>
        </div>
        {state.kind === "loaded" && <StatusBadge intake={state.intake} />}
      </CardHeader>
      <CardContent className="space-y-5">
        {state.kind === "loading" && <div aria-hidden="true" className="h-32 animate-pulse rounded-lg bg-muted" />}
        {state.kind === "error" && <FormError message={state.message} />}

        {state.kind === "loaded" && (
          <>
            <FormError message={actionError} />
            {busyMessage && <ThinkingNotice message={busyMessage} />}

            {state.intake.status === "NOT_STARTED" && (
              <IdeaForm
                initialIdea={project.description ?? ""}
                submitLabel="Start intake"
                disabled={busyMessage !== null}
                onSubmit={(idea) => run("Claude is reading your idea…", () => startIntake(project.id, idea))}
              />
            )}

            {state.intake.status === "AWAITING_ANSWERS" && (
              <AwaitingAnswers
                intake={state.intake}
                disabled={busyMessage !== null}
                onAnswer={(answers) =>
                  run("Claude is reviewing your answers…", () => answerIntake(project.id, answers))
                }
                onWriteBrief={(answers) =>
                  run("Claude is writing the brief…", () => writeIntakeBrief(project.id, answers))
                }
                onRestart={(idea) => run("Claude is reading your idea…", () => startIntake(project.id, idea))}
              />
            )}

            {state.intake.status === "BRIEF_READY" && state.intake.brief && (
              <BriefReady
                intake={state.intake}
                disabled={busyMessage !== null}
                onRegenerate={() => run("Claude is rewriting the brief…", () => writeIntakeBrief(project.id))}
                onRestart={(idea) => run("Claude is reading your idea…", () => startIntake(project.id, idea))}
              />
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function StatusBadge({ intake }: { intake: Intake }) {
  switch (intake.status) {
    case "NOT_STARTED":
      return <Badge variant="outline">Not started</Badge>;
    case "AWAITING_ANSWERS":
      return (
        <Badge variant="secondary">
          Round {intake.currentRound} of {intake.maxQuestionRounds}
        </Badge>
      );
    case "BRIEF_READY":
      return <Badge variant="success">Brief ready</Badge>;
  }
}

function ThinkingNotice({ message }: { message: string }) {
  return (
    <div role="status" className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
      <span className="size-2 animate-pulse rounded-full bg-primary" />
      <span>{message}</span>
      <span className="ml-auto text-xs text-muted-foreground">This can take up to a minute.</span>
    </div>
  );
}

function IdeaForm({
  initialIdea,
  submitLabel,
  disabled,
  onSubmit,
  onCancel,
}: {
  initialIdea: string;
  submitLabel: string;
  disabled: boolean;
  onSubmit: (idea: string) => void;
  onCancel?: () => void;
}) {
  const [idea, setIdea] = useState(initialIdea);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (idea.trim()) onSubmit(idea.trim());
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <Label htmlFor="intake-idea">What do you want to build?</Label>
      <Textarea
        id="intake-idea"
        rows={6}
        maxLength={IDEA_MAX}
        value={idea}
        disabled={disabled}
        placeholder="Who is it for, what problem does it solve, and what should people be able to do with it?"
        onChange={(event) => setIdea(event.target.value)}
      />
      <div className="flex items-center gap-2">
        <Button type="submit" disabled={disabled || !idea.trim()}>
          <Sparkles />
          {submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={disabled}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}

function AwaitingAnswers({
  intake,
  disabled,
  onAnswer,
  onWriteBrief,
  onRestart,
}: {
  intake: Intake;
  disabled: boolean;
  onAnswer: (answers: { questionId: string; answer: string }[]) => void;
  onWriteBrief: (answers: { questionId: string; answer: string }[]) => void;
  onRestart: (idea: string) => void;
}) {
  const currentRound = intake.rounds.find((round) => round.number === intake.currentRound);
  const previousRounds = intake.rounds.filter((round) => round.number !== intake.currentRound);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [isEditingIdea, setIsEditingIdea] = useState(false);

  // New questions arrived: start with empty answers.
  useEffect(() => setAnswers({}), [intake.currentRound]);

  const answerList = (currentRound?.questions ?? []).map((question) => ({
    questionId: question.id,
    answer: answers[question.id] ?? "",
  }));
  const isLastRound = intake.currentRound === intake.maxQuestionRounds;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onAnswer(answerList);
  }

  if (isEditingIdea) {
    return (
      <IdeaForm
        initialIdea={intake.idea ?? ""}
        submitLabel="Restart intake"
        disabled={disabled}
        onSubmit={onRestart}
        onCancel={() => setIsEditingIdea(false)}
      />
    );
  }

  return (
    <div className="space-y-5">
      <IdeaSummary idea={intake.idea} onEdit={() => setIsEditingIdea(true)} disabled={disabled} />
      {previousRounds.length > 0 && <AnsweredRounds rounds={previousRounds} />}

      {currentRound && (
        <form onSubmit={handleSubmit} className="space-y-4">
          <p className="flex items-center gap-2 text-sm font-medium">
            <MessageCircleQuestion className="size-4 text-primary" />
            Claude has {currentRound.questions.length === 1 ? "a question" : "a few questions"}
          </p>
          {currentRound.questions.map((question, index) => (
            <div key={question.id} className="space-y-2">
              <Label htmlFor={`answer-${question.id}`} className="leading-snug">
                {index + 1}. {question.question}
              </Label>
              {question.rationale && <p className="text-xs text-muted-foreground">{question.rationale}</p>}
              <Textarea
                id={`answer-${question.id}`}
                rows={2}
                maxLength={ANSWER_MAX}
                className="min-h-16"
                placeholder="Your answer (leave blank to skip)"
                value={answers[question.id] ?? ""}
                disabled={disabled}
                onChange={(event) => setAnswers((current) => ({ ...current, [question.id]: event.target.value }))}
              />
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" disabled={disabled}>
              {isLastRound ? "Submit and write brief" : "Submit answers"}
            </Button>
            {!isLastRound && (
              <Button type="button" variant="outline" disabled={disabled} onClick={() => onWriteBrief(answerList)}>
                Write the brief now
              </Button>
            )}
          </div>
        </form>
      )}
    </div>
  );
}

function BriefReady({
  intake,
  disabled,
  onRegenerate,
  onRestart,
}: {
  intake: Intake;
  disabled: boolean;
  onRegenerate: () => void;
  onRestart: (idea: string) => void;
}) {
  const [isEditingIdea, setIsEditingIdea] = useState(false);

  if (isEditingIdea) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-muted-foreground">
          Restarting replaces the current questions, answers and brief.
        </p>
        <IdeaForm
          initialIdea={intake.idea ?? ""}
          submitLabel="Restart intake"
          disabled={disabled}
          onSubmit={onRestart}
          onCancel={() => setIsEditingIdea(false)}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {intake.brief && <ProjectBriefView brief={intake.brief} />}

      <div className="flex flex-wrap items-center gap-2 border-t pt-4">
        <Button variant="outline" size="sm" onClick={onRegenerate} disabled={disabled}>
          <Sparkles />
          Regenerate brief
        </Button>
        <Button variant="ghost" size="sm" onClick={() => setIsEditingIdea(true)} disabled={disabled}>
          <RotateCcw />
          Start over
        </Button>
        {intake.updatedAt && (
          <span className="ml-auto text-xs text-muted-foreground">
            Written {formatDateTime(intake.updatedAt)}
            {intake.briefModel && <> · {intake.briefModel}</>}
          </span>
        )}
      </div>

      <details className="group rounded-lg border">
        <summary className="cursor-pointer px-4 py-3 text-sm font-medium select-none">
          Idea and clarifications
        </summary>
        <div className="space-y-4 border-t px-4 py-4">
          <IdeaSummary idea={intake.idea} />
          {intake.rounds.length > 0 ? (
            <AnsweredRounds rounds={intake.rounds} />
          ) : (
            <p className="text-sm text-muted-foreground">Claude wrote the brief without needing to ask questions.</p>
          )}
        </div>
      </details>
    </div>
  );
}

function IdeaSummary({ idea, onEdit, disabled }: { idea: string | null; onEdit?: () => void; disabled?: boolean }) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">Your idea</p>
        {onEdit && (
          <Button variant="link" size="sm" className="h-auto p-0 text-xs" onClick={onEdit} disabled={disabled}>
            Change idea
          </Button>
        )}
      </div>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{idea}</p>
    </div>
  );
}

function AnsweredRounds({ rounds }: { rounds: IntakeRound[] }) {
  return (
    <div className="space-y-4">
      {rounds.map((round) => (
        <div key={round.number} className="space-y-2">
          <p className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">Round {round.number}</p>
          <dl className="space-y-3">
            {round.questions.map((question) => (
              <div key={question.id} className="text-sm">
                <dt className="font-medium">{question.question}</dt>
                <dd className={question.answer ? "mt-0.5 text-muted-foreground" : "mt-0.5 text-muted-foreground italic"}>
                  {question.answer ?? "Skipped"}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      ))}
    </div>
  );
}
