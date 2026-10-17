import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";

import { useAuth } from "@/auth/auth-context";
import { BrandMark } from "@/components/layout/brand-mark";

/** Renders its children only for signed-in users; everyone else is sent to the login page. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return (
      <div className="grid min-h-svh place-items-center" role="status" aria-label="Loading">
        <div className="animate-pulse">
          <BrandMark />
        </div>
      </div>
    );
  }

  if (status === "anonymous") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}
