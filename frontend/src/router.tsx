import { createBrowserRouter } from "react-router";

import { RequireAuth } from "@/auth/require-auth";
import { AppLayout } from "@/components/layout/app-layout";
import { AuthLayout } from "@/components/layout/auth-layout";
import { lazyPage } from "@/components/layout/lazy-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { LoginPage } from "@/pages/login-page";
import { NewProjectPage } from "@/pages/new-project-page";
import { NotFoundPage } from "@/pages/not-found-page";
import { ProjectDetailPage } from "@/pages/project-detail-page";
import { ProjectsPage } from "@/pages/projects-page";
import { RegisterPage } from "@/pages/register-page";

// The stage pages are loaded when first opened, so the first visit downloads less
// (Mermaid and the Markdown renderer are only fetched by the pages that use them).
const RequirementsPage = lazyPage(() => import("@/pages/requirements-page").then((m) => m.RequirementsPage));
const ArchitecturePage = lazyPage(() => import("@/pages/architecture-page").then((m) => m.ArchitecturePage));
const DatabaseDesignPage = lazyPage(() => import("@/pages/database-design-page").then((m) => m.DatabaseDesignPage));
const ApiDesignPage = lazyPage(() => import("@/pages/api-design-page").then((m) => m.ApiDesignPage));
const TasksPage = lazyPage(() => import("@/pages/tasks-page").then((m) => m.TasksPage));
const CodePage = lazyPage(() => import("@/pages/code-page").then((m) => m.CodePage));
const ReviewPage = lazyPage(() => import("@/pages/review-page").then((m) => m.ReviewPage));
const DocsPage = lazyPage(() => import("@/pages/docs-page").then((m) => m.DocsPage));
const UsagePage = lazyPage(() => import("@/pages/usage-page").then((m) => m.UsagePage));

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
    ],
  },
  {
    path: "/",
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <DashboardPage />, handle: { title: "Dashboard" } },
      { path: "projects", element: <ProjectsPage />, handle: { title: "Projects" } },
      { path: "projects/new", element: <NewProjectPage />, handle: { title: "New project" } },
      { path: "projects/:projectId", element: <ProjectDetailPage />, handle: { title: "Project" } },
      { path: "projects/:projectId/requirements", element: <RequirementsPage />, handle: { title: "Requirements" } },
      { path: "projects/:projectId/architecture", element: <ArchitecturePage />, handle: { title: "Architecture" } },
      { path: "projects/:projectId/database-design", element: <DatabaseDesignPage />, handle: { title: "Database design" } },
      { path: "projects/:projectId/api-design", element: <ApiDesignPage />, handle: { title: "API design" } },
      { path: "projects/:projectId/tasks", element: <TasksPage />, handle: { title: "Development tasks" } },
      { path: "projects/:projectId/code", element: <CodePage />, handle: { title: "Code" } },
      { path: "projects/:projectId/review", element: <ReviewPage />, handle: { title: "Code review" } },
      { path: "projects/:projectId/docs", element: <DocsPage />, handle: { title: "Documentation" } },
      { path: "usage", element: <UsagePage />, handle: { title: "AI usage" } },
      { path: "*", element: <NotFoundPage />, handle: { title: "Not found" } },
    ],
  },
]);
