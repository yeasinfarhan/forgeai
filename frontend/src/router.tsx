import { createBrowserRouter } from "react-router";

import { RequireAuth } from "@/auth/require-auth";
import { AppLayout } from "@/components/layout/app-layout";
import { AuthLayout } from "@/components/layout/auth-layout";
import { ArchitecturePage } from "@/pages/architecture-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { LoginPage } from "@/pages/login-page";
import { NewProjectPage } from "@/pages/new-project-page";
import { NotFoundPage } from "@/pages/not-found-page";
import { ProjectDetailPage } from "@/pages/project-detail-page";
import { ProjectsPage } from "@/pages/projects-page";
import { RegisterPage } from "@/pages/register-page";
import { RequirementsPage } from "@/pages/requirements-page";

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
      { path: "*", element: <NotFoundPage />, handle: { title: "Not found" } },
    ],
  },
]);
