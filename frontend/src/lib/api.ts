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

// --- Database & API design ---------------------------------------------------

/** A design document with its review state. */
export interface DesignState<T> {
  status: ReviewStatus;
  content: T;
  model: string | null;
  edited: boolean;
  revision: number;
  version: number;
  /** The earlier stages it was generated from have changed since. */
  inputsChanged: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Finding {
  severity: "ERROR" | "WARNING";
  area: "DATABASE" | "API" | "REQUIREMENTS" | "TASKS";
  message: string;
}

/** What both design pages receive. */
export interface StageOverview<T> {
  design: DesignState<T> | null;
  requirements: RequirementRef[];
  canGenerate: boolean;
  /** Why generating is not possible right now. */
  blocker: string | null;
  findings: Finding[];
}

export interface DesignRevisionSummary {
  revision: number;
  change: "GENERATED" | "EDITED" | "STATUS_CHANGED";
  status: ReviewStatus;
  model: string | null;
  createdAt: string;
}

export interface DesignRevisionDetail<T> extends DesignRevisionSummary {
  content: T;
}

export type ColumnType =
  | "UUID"
  | "TEXT"
  | "VARCHAR"
  | "INTEGER"
  | "BIGINT"
  | "DECIMAL"
  | "BOOLEAN"
  | "DATE"
  | "TIMESTAMP"
  | "JSON";

export type OnDelete = "CASCADE" | "RESTRICT" | "SET_NULL";

export interface DbColumn {
  name: string;
  type: ColumnType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  nullable: boolean;
  primaryKey: boolean;
  unique: boolean;
  defaultValue: string | null;
  references: { entity: string; column: string; onDelete: OnDelete } | null;
  allowedValues: string[];
  description: string | null;
}

export interface DbIndex {
  columns: string[];
  unique: boolean;
}

export interface DbEntity {
  name: string;
  description: string | null;
  columns: DbColumn[];
  indexes: DbIndex[];
  requirements: string[];
}

export interface DatabaseDesignContent {
  summary: string;
  entities: DbEntity[];
}

export type DatabaseDesignOverview = StageOverview<DatabaseDesignContent>;

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type FieldType = "STRING" | "INTEGER" | "NUMBER" | "BOOLEAN" | "UUID" | "DATE" | "DATE_TIME" | "EMAIL" | "OBJECT";

export interface ApiErrorCase {
  status: number;
  code: string;
  description: string;
}

export interface ApiField {
  name: string;
  type: FieldType;
  /** Schema name, for OBJECT. */
  ref: string | null;
  array: boolean;
  required: boolean;
  description: string | null;
}

export interface ApiSchema {
  name: string;
  description: string | null;
  entity: string | null;
  fields: ApiField[];
}

export interface ApiParameter {
  name: string;
  type: FieldType;
  required: boolean;
  description: string | null;
}

export interface ApiEndpoint {
  method: HttpMethod;
  path: string;
  summary: string;
  description: string | null;
  authenticated: boolean;
  entity: string | null;
  requestSchema: string | null;
  responseSchema: string | null;
  responseList: boolean;
  successStatus: number;
  pathParameters: ApiParameter[];
  queryParameters: ApiParameter[];
  errorStatuses: number[];
  requirements: string[];
}

export interface ApiDesignContent {
  summary: string;
  basePath: string;
  authentication: string;
  errorFormat: string;
  errors: ApiErrorCase[];
  schemas: ApiSchema[];
  endpoints: ApiEndpoint[];
}

export interface ApiDesignOverview extends StageOverview<ApiDesignContent> {
  /** The current data model, for linking schemas and endpoints to entities. */
  database: DatabaseDesignContent | null;
}

export type DesignStage = "database-design" | "api-design";

function designPath(projectId: string, stage: DesignStage, suffix = "") {
  return `/api/projects/${encodeURIComponent(projectId)}/${stage}${suffix}`;
}

export function getDesign<O>(projectId: string, stage: DesignStage, signal?: AbortSignal): Promise<O> {
  return apiRequest<O>(designPath(projectId, stage), { signal });
}

export function generateDesign<O>(projectId: string, stage: DesignStage): Promise<O> {
  return apiRequest<O>(designPath(projectId, stage, "/generate"), { method: "POST" });
}

export function updateDesign<O, T>(projectId: string, stage: DesignStage, design: DesignState<T>, content: T): Promise<O> {
  return apiRequest<O>(designPath(projectId, stage), { method: "PUT", body: { content, version: design.version } });
}

export function changeDesignStatus<O, T>(
  projectId: string,
  stage: DesignStage,
  design: DesignState<T>,
  status: ReviewStatus,
): Promise<O> {
  return apiRequest<O>(designPath(projectId, stage, "/status"), { method: "POST", body: { status, version: design.version } });
}

export function getDesignHistory(projectId: string, stage: DesignStage): Promise<DesignRevisionSummary[]> {
  return apiRequest<DesignRevisionSummary[]>(designPath(projectId, stage, "/revisions"));
}

export function getDesignRevision<T>(projectId: string, stage: DesignStage, revision: number): Promise<DesignRevisionDetail<T>> {
  return apiRequest<DesignRevisionDetail<T>>(designPath(projectId, stage, `/revisions/${revision}`));
}

export function designExportPath(projectId: string, stage: DesignStage) {
  return designPath(projectId, stage, stage === "database-design" ? "/export.sql" : "/openapi.json");
}

/** A file from the API (an export), with the name the server suggests. */
export async function downloadFile(path: string): Promise<{ fileName: string; text: string }> {
  const headers: Record<string, string> = { Accept: "*/*" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { headers });
  } catch {
    throw new ApiError(0, "Could not reach the ForgeAI server. Check that the backend is running.");
  }
  if (response.status === 401 && accessToken) unauthorizedHandler?.();
  if (!response.ok) throw await toApiError(response);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/.exec(disposition);
  return { fileName: match?.[1] ?? "export.txt", text: await response.text() };
}

// --- Development tasks ----------------------------------------------------------

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";
export type TaskType = "SETUP" | "DATABASE" | "BACKEND" | "FRONTEND" | "TESTING" | "DEVOPS" | "DOCUMENTATION";

export interface Task {
  id: string;
  code: string;
  position: number;
  title: string;
  description: string;
  type: TaskType;
  estimate: number | null;
  status: TaskStatus;
  source: "AI" | "USER";
  edited: boolean;
  acceptanceCriteria: string[];
  dependsOn: string[];
  requirements: string[];
  entities: string[];
  /** "METHOD /path", relative to the API base path. */
  endpoints: string[];
  components: string[];
  /** Tasks it depends on that are not done yet. */
  blockedBy: string[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TaskInput {
  title: string;
  description: string;
  type: TaskType;
  estimate: number | null;
  acceptanceCriteria: string[];
  dependsOn: string[];
  requirements: string[];
  entities: string[];
  endpoints: string[];
  components: string[];
}

export interface BacklogOverview {
  tasks: Task[];
  counts: { todo: number; inProgress: number; done: number; total: number; points: number; pointsDone: number };
  /** What tasks can link to. */
  refs: { requirements: RequirementRef[]; entities: string[]; endpoints: string[]; components: string[] };
  canGenerate: boolean;
  blocker: string | null;
  generatedAt: string | null;
  model: string | null;
  inputsChanged: boolean;
  findings: Finding[];
}

export interface TaskEvent {
  type: "CREATED" | "EDITED" | "STATUS_CHANGED";
  fromStatus: TaskStatus | null;
  toStatus: TaskStatus | null;
  createdAt: string;
}

function tasksPath(projectId: string, suffix = "") {
  return `/api/projects/${encodeURIComponent(projectId)}/tasks${suffix}`;
}

export function getBacklog(projectId: string, signal?: AbortSignal): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId), { signal });
}

export function generateTasks(projectId: string): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId, "/generate"), { method: "POST" });
}

export function createTask(projectId: string, input: TaskInput): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId), { method: "POST", body: input });
}

export function updateTask(projectId: string, task: Task, input: TaskInput): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId, `/${task.id}`), { method: "PUT", body: { ...input, version: task.version } });
}

export function changeTaskStatus(projectId: string, task: Task, status: TaskStatus): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId, `/${task.id}/status`), {
    method: "POST",
    body: { status, version: task.version },
  });
}

export function moveTask(projectId: string, task: Task, position: number): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId, `/${task.id}/move`), {
    method: "POST",
    body: { position, version: task.version },
  });
}

export function deleteTask(projectId: string, task: Task): Promise<BacklogOverview> {
  return apiRequest<BacklogOverview>(tasksPath(projectId, `/${task.id}`), { method: "DELETE" });
}

export function getTaskEvents(projectId: string, task: Task): Promise<TaskEvent[]> {
  return apiRequest<TaskEvent[]>(tasksPath(projectId, `/${task.id}/events`));
}

// --- Code -------------------------------------------------------------------------

export type ChangeAction = "CREATE" | "UPDATE" | "DELETE";
export type ChangeDecision = "PENDING" | "ACCEPTED" | "REJECTED";
export type ChangesetStatus = "PENDING" | "COMPLETED" | "DISCARDED";

export interface CodeFileSummary {
  path: string;
  lines: number;
  size: number;
  lastTaskCode: string | null;
  updatedAt: string;
}

export interface CodeFileContent {
  path: string;
  content: string;
  lastTaskCode: string | null;
  updatedAt: string;
}

export interface CodeTaskItem {
  id: string;
  code: string;
  title: string;
  status: TaskStatus;
  blockedBy: string[];
  pendingChangesetId: string | null;
}

export interface ChangesetSummary {
  id: string;
  taskId: string | null;
  taskCode: string;
  taskTitle: string;
  status: ChangesetStatus;
  summary: string;
  pending: number;
  accepted: number;
  rejected: number;
  createdAt: string;
}

export interface CodeChange {
  id: string;
  path: string;
  action: ChangeAction;
  baseContent: string | null;
  newContent: string | null;
  explanation: string;
  decision: ChangeDecision;
  /** The file changed after this was proposed; it can no longer be accepted. */
  stale: boolean;
  decidedAt: string | null;
}

export interface Changeset {
  id: string;
  taskId: string | null;
  taskCode: string;
  taskTitle: string;
  status: ChangesetStatus;
  summary: string;
  model: string | null;
  createdAt: string;
  completedAt: string | null;
  changes: CodeChange[];
}

export interface CodeOverview {
  files: CodeFileSummary[];
  tasks: CodeTaskItem[];
  changesets: ChangesetSummary[];
  canGenerate: boolean;
  blocker: string | null;
}

function codePath(projectId: string, suffix = "") {
  return `/api/projects/${encodeURIComponent(projectId)}/code${suffix}`;
}

export function getCode(projectId: string, signal?: AbortSignal): Promise<CodeOverview> {
  return apiRequest<CodeOverview>(codePath(projectId), { signal });
}

export function getCodeFile(projectId: string, path: string, signal?: AbortSignal): Promise<CodeFileContent> {
  return apiRequest<CodeFileContent>(codePath(projectId, `/file?path=${encodeURIComponent(path)}`), { signal });
}

export function generateCode(projectId: string, taskId: string): Promise<Changeset> {
  return apiRequest<Changeset>(codePath(projectId, `/tasks/${taskId}/generate`), { method: "POST" });
}

export function getChangeset(projectId: string, changesetId: string, signal?: AbortSignal): Promise<Changeset> {
  return apiRequest<Changeset>(codePath(projectId, `/changesets/${changesetId}`), { signal });
}

export function decideChange(
  projectId: string,
  changesetId: string,
  changeId: string,
  decision: "accept" | "reject",
): Promise<Changeset> {
  return apiRequest<Changeset>(codePath(projectId, `/changesets/${changesetId}/changes/${changeId}/${decision}`), {
    method: "POST",
  });
}

export function acceptAllChanges(projectId: string, changesetId: string): Promise<Changeset> {
  return apiRequest<Changeset>(codePath(projectId, `/changesets/${changesetId}/accept-all`), { method: "POST" });
}

export function discardChangeset(projectId: string, changesetId: string): Promise<Changeset> {
  return apiRequest<Changeset>(codePath(projectId, `/changesets/${changesetId}/discard`), { method: "POST" });
}

export function codeExportPath(projectId: string) {
  return codePath(projectId, "/export.zip");
}

/** A binary file from the API (e.g. a ZIP export), with the name the server suggests. */
export async function downloadBinary(path: string): Promise<{ fileName: string; blob: Blob }> {
  const headers: Record<string, string> = { Accept: "*/*" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { headers });
  } catch {
    throw new ApiError(0, "Could not reach the ForgeAI server. Check that the backend is running.");
  }
  if (response.status === 401 && accessToken) unauthorizedHandler?.();
  if (!response.ok) throw await toApiError(response);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/.exec(disposition);
  return { fileName: match?.[1] ?? "export.zip", blob: await response.blob() };
}
