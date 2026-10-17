import { Navigate, Outlet, useLocation } from "react-router";

import { useAuth } from "@/auth/auth-context";
import { BrandMark } from "@/components/layout/brand-mark";

/** Centered layout for the sign-in and sign-up pages. Signed-in users are sent to the app. */
export function AuthLayout() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === "authenticated") {
    const from = (location.state as { from?: string } | null)?.from ?? "/";
    return <Navigate to={from} replace />;
  }

  return (
    <div className="relative flex min-h-svh flex-col items-center justify-center px-4 py-12">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 h-72"
        style={{
          background:
            "radial-gradient(ellipse at top, color-mix(in oklch, var(--primary) 14%, transparent), transparent 70%)",
        }}
      />
      <div className="relative w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2.5">
          <BrandMark />
          <span className="text-lg font-semibold tracking-tight">ForgeAI</span>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
