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

  constructor(status: number, message: string, violations: FieldViolation[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.violations = violations;
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
  try {
    const problem = (await response.json()) as { detail?: string; title?: string; violations?: FieldViolation[] };
    message = problem.detail ?? problem.title ?? message;
    violations = problem.violations ?? [];
  } catch {
    // Body was empty or not JSON; keep the generic message.
  }
  return new ApiError(response.status, message, violations);
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
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
