import { useState } from "react";
import { Outlet, useOutletContext } from "react-router";

import { Header } from "@/components/layout/header";
import { Sidebar } from "@/components/layout/sidebar";
import { useHealthCheck, type HealthCheck } from "@/hooks/use-health-check";

export interface AppLayoutContext {
  health: HealthCheck;
}

export function AppLayout() {
  const health = useHealthCheck();
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);

  return (
    <div className="flex min-h-svh bg-background">
      <Sidebar isMobileOpen={isMobileNavOpen} onNavigate={() => setIsMobileNavOpen(false)} />

      <div className="flex min-w-0 flex-1 flex-col">
        <Header health={health} onOpenNavigation={() => setIsMobileNavOpen(true)} />
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div className="mx-auto w-full max-w-6xl">
            <Outlet context={{ health } satisfies AppLayoutContext} />
          </div>
        </main>
      </div>
    </div>
  );
}

export function useAppLayoutContext() {
  return useOutletContext<AppLayoutContext>();
}
