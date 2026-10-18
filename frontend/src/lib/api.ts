/**
 * HTTP client for the ForgeAI backend.
 *
 * Requests use relative /api paths. In development Vite proxies them to the backend;
 * in Docker, nginx does the same. VITE_API_BASE_URL can point elsewhere if needed.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export interface FieldViolation {
  field: string;
  message: string;
}

/** An error response from the API, parsed from its RFC 9457 problem-details body. */
export class ApiError extends Error {
  readonly status: number;
  readonly violations: FieldViolation[];
  /** Rule violations the server explains in words (e.g. a reference to something that does not exist). */
  readonly problems: string[];

  constructor(status: number, message: string, violations: FieldViolation[] = [], problems: string[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.violations = violations;
    this.problems = problems;
  }
}

let accessToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

/** Sets the bearer token sent with every request. Managed by the auth provider. */
export function setAccessToken(token: string | null) {
  accessToken = token;
}

/** Called when an authenticated request is rejected with 401 (e.g. the token expired). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
}

export async function apiRequest<T>(path: string, { method = "GET", body, signal }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError(0, "Could not reach the ForgeAI server. Check that the backend is running.");
  }

  if (response.status === 401 && accessToken) {
    unauthorizedHandler?.();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  let message = `Request failed with status ${response.status}`;
  let violations: FieldViolation[] = [];
  let problems: string[] = [];
  try {
    const problem = (await response.json()) as {
      detail?: string;
      title?: string;
      violations?: FieldViolation[];
      problems?: string[];
    };
    message = problem.detail ?? problem.title ?? message;
    violations = problem.violations ?? [];
    problems = problem.problems ?? [];
  } catch {
    // Body was empty or not JSON; keep the generic message.
  }
  return new ApiError(response.status, message, violations, problems);
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** Every message in an error: rule problems, field violations, or just the main message. */
export function errorDetails(error: unknown): string[] {
  if (error instanceof ApiError) {
    if (error.problems.length > 0) return error.problems;
    if (error.violations.length > 0) return error.violations.map((v) => `${v.field}: ${v.message}`);
  }
  return [errorMessage(error)];
}

/** A user-facing message for any error thrown by the API client. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "Something went wrong. Please try again.";
}

// --- Health ------------------------------------------------------------------

export interface HealthResponse {
  status: string;
  service: string;
}

export function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return apiRequest<HealthResponse>("/api/health", { signal });
}

// --- Auth --------------------------------------------------------------------

export interface User {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
}

export function register(input: RegisterInput): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/api/auth/register", { method: "POST", body: input });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/api/auth/login", { method: "POST", body: { email, password } });
}

export function fetchCurrentUser(signal?: AbortSignal): Promise<User> {
  return apiRequest<User>("/api/auth/me", { signal });
}

// --- Projects ----------------------------------------------------------------

export interface Project {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectInput {
  name: string;
  description: string;
}

export function listProjects(signal?: AbortSignal): Promise<Project[]> {
  return apiRequest<Project[]>("/api/projects", { signal });
}

export function getProject(id: string, signal?: AbortSignal): Promise<Project> {
  return apiRequest<Project>(`/api/projects/${encodeURIComponent(id)}`, { signal });
}

export function createProject(input: ProjectInput): Promise<Project> {
  return apiRequest<Project>("/api/projects", { method: "POST", body: input });
}

export function updateProject(id: string, input: ProjectInput): Promise<Project> {
  return apiRequest<Project>(`/api/projects/${encodeURIComponent(id)}`, { method: "PUT", body: input });
}

export function deleteProject(id: string): Promise<void> {
  return apiRequest<void>(`/api/projects/${encodeURIComponent(id)}`, { method: "DELETE" });
}

// --- Idea intake -------------------------------------------------------------

export type IntakeStatus = "NOT_STARTED" | "AWAITING_ANSWERS" | "BRIEF_READY";

export interface IntakeQuestion {
  id: string;
  question: string;
  rationale: string;
  answer: string | null;
}

export interface IntakeRound {
  number: number;
  questions: IntakeQuestion[];
}

export interface ProjectBrief {
  title: string;
  summary: string;
  problem: string;
  targetUsers: string[];
  goals: string[];
  coreFeatures: { name: string; description: string }[];
  outOfScope: string[];
  constraints: string[];
  openQuestions: string[];
}

export interface Intake {
  status: IntakeStatus;
  idea: string | null;
  currentRound: number | null;
  maxQuestionRounds: number;
  rounds: IntakeRound[];
  brief: ProjectBrief | null;
  briefModel: string | null;
  updatedAt: string | null;
}

export interface IntakeAnswer {
  questionId: string;
  answer: string;
}

function intakePath(projectId: string, suffix = "") {
  return `/api/projects/${encodeURIComponent(projectId)}/intake${suffix}`;
}

export function getIntake(projectId: string, signal?: AbortSignal): Promise<Intake> {
  return apiRequest<Intake>(intakePath(projectId), { signal });
}

export function startIntake(projectId: string, idea: string): Promise<Intake> {
  return apiRequest<Intake>(intakePath(projectId), { method: "POST", body: { idea } });
}

export function answerIntake(projectId: string, answers: IntakeAnswer[]): Promise<Intake> {
  return apiRequest<Intake>(intakePath(projectId, "/answers"), { method: "POST", body: { answers } });
}

export function writeIntakeBrief(projectId: string, answers: IntakeAnswer[] = []): Promise<Intake> {
  return apiRequest<Intake>(intakePath(projectId, "/brief"), { method: "POST", body: { answers } });
}

// --- Requirements ------------------------------------------------------------

export type RequirementType = "FUNCTIONAL" | "NON_FUNCTIONAL";
export type RequirementPriority = "MUST" | "SHOULD" | "COULD";
export type RequirementStatus = "DRAFT" | "IN_REVIEW" | "APPROVED";

export interface Requirement {
  id: string;
  code: string;
  type: RequirementType;
  category: string | null;
  title: string;
  description: string;
  acceptanceCriteria: string[];
  priority: RequirementPriority;
  status: RequirementStatus;
  source: "AI" | "USER";
  edited: boolean;
  version: number;
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface RequirementsOverview {
  requirements: Requirement[];
  counts: { draft: number; inReview: number; approved: number; total: number };
  briefReady: boolean;
  allApproved: boolean;
}

export interface RequirementRevision {
  revision: number;
  change: "CREATED" | "EDITED" | "STATUS_CHANGED";
  category: string | null;
  title: string;
  description: string;
  acceptanceCriteria: string[];
  priority: RequirementPriority;
  status: RequirementStatus;
  createdAt: string;
}

export interface RequirementInput {
  category: string;
  title: string;
  description: string;
  acceptanceCriteria: string[];
  priority: RequirementPriority;
}

function requirementsPath(projectId: string, suffix = "") {
  return `/api/projects/${encodeURIComponent(projectId)}/requirements${suffix}`;
}

export function getRequirements(projectId: string, signal?: AbortSignal): Promise<RequirementsOverview> {
  return apiRequest<RequirementsOverview>(requirementsPath(projectId), { signal });
}

export function generateRequirements(projectId: string): Promise<RequirementsOverview> {
  return apiRequest<RequirementsOverview>(requirementsPath(projectId, "/generate"), { method: "POST" });
}

export function createRequirement(projectId: string, type: RequirementType, input: RequirementInput): Promise<Requirement> {
  return apiRequest<Requirement>(requirementsPath(projectId), { method: "POST", body: { type, ...input } });
}

export function updateRequirement(projectId: string, requirement: Requirement, input: RequirementInput): Promise<Requirement> {
  return apiRequest<Requirement>(requirementsPath(projectId, `/${requirement.id}`), {
    method: "PUT",
    body: { ...input, version: requirement.version },
  });
}

export function changeRequirementStatus(
  projectId: string,
  requirement: Requirement,
  status: RequirementStatus,
): Promise<Requirement> {
  return apiRequest<Requirement>(requirementsPath(projectId, `/${requirement.id}/status`), {
    method: "POST",
    body: { status, version: requirement.version },
  });
}

export function bulkChangeRequirementStatus(
  projectId: string,
  from: RequirementStatus,
  to: RequirementStatus,
): Promise<RequirementsOverview> {
  return apiRequest<RequirementsOverview>(requirementsPath(projectId, "/bulk-status"), {
    method: "POST",
    body: { from, to },
  });
}

export function deleteRequirement(projectId: string, requirementId: string): Promise<void> {
  return apiRequest<void>(requirementsPath(projectId, `/${requirementId}`), { method: "DELETE" });
}

export function getRequirementHistory(projectId: string, requirementId: string): Promise<RequirementRevision[]> {
  return apiRequest<RequirementRevision[]>(requirementsPath(projectId, `/${requirementId}/history`));
}

// --- Architecture ------------------------------------------------------------

/** The review workflow shared by requirements and architecture. */
export type ReviewStatus = RequirementStatus;

export type ComponentKind = "CLIENT" | "SERVICE" | "DATA_STORE" | "EXTERNAL" | "INFRASTRUCTURE";

export interface ArchitectureComponent {
  name: string;
  kind: ComponentKind;
  responsibility: string;
  technology: string | null;
  dependsOn: string[];
  requirements: string[];
}

export interface ArchitectureDecision {
  title: string;
  context: string;
  decision: string;
  alternatives: string[];
  consequences: string;
  requirements: string[];
}

export interface ArchitectureFlow {
  title: string;
  description: string | null;
  /** Mermaid source, normally a sequenceDiagram. */
  diagram: string;
}

export interface ArchitectureContent {
  overview: string;
  style: string;
  styleRationale: string;
  components: ArchitectureComponent[];
  decisions: ArchitectureDecision[];
  flows: ArchitectureFlow[];
}

export interface Architecture {
  status: ReviewStatus;
  content: ArchitectureContent;
  model: string | null;
  edited: boolean;
  revision: number;
  version: number;
  /** The approved requirements changed since this content was generated. */
  requirementsChanged: boolean;
  /** Approved requirement codes that no component or decision refers to. */
  uncoveredRequirements: string[];
  createdAt: string;
  updatedAt: string;
}

export interface RequirementRef {
  code: string;
  type: RequirementType;
  title: string;
  priority: RequirementPriority;
}

export interface ArchitectureOverview {
  architecture: Architecture | null;
  /** The approved requirements the architecture is built from. */
  requirements: RequirementRef[];
  requirementsTotal: number;
  requirementsApproved: number;
  canGenerate: boolean;
}

export interface ArchitectureRevisionSummary {
  revision: number;
  change: "GENERATED" | "EDITED" | "STATUS_CHANGED";
  status: ReviewStatus;
  model: string | null;
  createdAt: string;
}

export interface ArchitectureRevisionDetail extends ArchitectureRevisionSummary {
  content: ArchitectureContent;
}

function architecturePath(projectId: string, suffix = "") {
  return `/api/projects/${encodeURIComponent(projectId)}/architecture${suffix}`;
}

export function getArchitecture(projectId: string, signal?: AbortSignal): Promise<ArchitectureOverview> {
  return apiRequest<ArchitectureOverview>(architecturePath(projectId), { signal });
}

export function generateArchitecture(projectId: string): Promise<ArchitectureOverview> {
  return apiRequest<ArchitectureOverview>(architecturePath(projectId, "/generate"), { method: "POST" });
}

export function updateArchitecture(
  projectId: string,
  architecture: Architecture,
  content: ArchitectureContent,
): Promise<ArchitectureOverview> {
  return apiRequest<ArchitectureOverview>(architecturePath(projectId), {
    method: "PUT",
    body: { content, version: architecture.version },
  });
}

export function changeArchitectureStatus(
  projectId: string,
  architecture: Architecture,
  status: ReviewStatus,
): Promise<ArchitectureOverview> {
  return apiRequest<ArchitectureOverview>(architecturePath(projectId, "/status"), {
    method: "POST",
    body: { status, version: architecture.version },
  });
}

export function getArchitectureHistory(projectId: string): Promise<ArchitectureRevisionSummary[]> {
  return apiRequest<ArchitectureRevisionSummary[]>(architecturePath(projectId, "/revisions"));
}

export function getArchitectureRevision(projectId: string, revision: number): Promise<ArchitectureRevisionDetail> {
  return apiRequest<ArchitectureRevisionDetail>(architecturePath(projectId, `/revisions/${revision}`));
}
