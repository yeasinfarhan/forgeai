import { createBrowserRouter } from "react-router";

import { RequireAuth } from "@/auth/require-auth";
import { AppLayout } from "@/components/layout/app-layout";
import { AuthLayout } from "@/components/layout/auth-layout";
import { ApiDesignPage } from "@/pages/api-design-page";
import { ArchitecturePage } from "@/pages/architecture-page";
import { CodePage } from "@/pages/code-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { DatabaseDesignPage } from "@/pages/database-design-page";
import { LoginPage } from "@/pages/login-page";
import { NewProjectPage } from "@/pages/new-project-page";
import { NotFoundPage } from "@/pages/not-found-page";
import { ProjectDetailPage } from "@/pages/project-detail-page";
import { ProjectsPage } from "@/pages/projects-page";
import { RegisterPage } from "@/pages/register-page";
import { RequirementsPage } from "@/pages/requirements-page";
import { ReviewPage } from "@/pages/review-page";
import { TasksPage } from "@/pages/tasks-page";

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
      { path: "*", element: <NotFoundPage />, handle: { title: "Not found" } },
    ],
  },
]);
