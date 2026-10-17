import { LogOut, Menu } from "lucide-react";
import { useMatches } from "react-router";

import { useAuth } from "@/auth/auth-context";
import { ApiStatusIndicator } from "@/components/api-status-indicator";
import { Button } from "@/components/ui/button";
import type { HealthCheck } from "@/hooks/use-health-check";

interface RouteHandle {
  title?: string;
}

interface HeaderProps {
  health: HealthCheck;
  onOpenNavigation: () => void;
}

export function Header({ health, onOpenNavigation }: HeaderProps) {
  const { user, logout } = useAuth();
  const matches = useMatches();
  const title =
    matches
      .map((match) => (match.handle as RouteHandle | undefined)?.title)
      .filter(Boolean)
      .at(-1) ?? "ForgeAI";

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b bg-background/80 px-4 backdrop-blur sm:px-6 lg:px-8">
      <Button variant="ghost" size="icon" className="md:hidden" onClick={onOpenNavigation} aria-label="Open navigation">
        <Menu />
      </Button>

      <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-2 text-sm">
        <span className="text-muted-foreground">Workspace</span>
        <span className="text-muted-foreground/50">/</span>
        <span className="truncate font-medium">{title}</span>
      </nav>

      <div className="ml-auto flex items-center gap-3">
        <span className="hidden sm:inline-flex">
          <ApiStatusIndicator state={health.state} />
        </span>
        {user && (
          <div className="flex items-center gap-2 border-l pl-3">
            <span
              aria-hidden="true"
              className="grid size-7 place-items-center rounded-full bg-primary/15 text-xs font-semibold text-primary"
            >
              {user.displayName.trim().charAt(0).toUpperCase()}
            </span>
            <div className="hidden min-w-0 leading-tight md:block">
              <p className="truncate text-sm font-medium">{user.displayName}</p>
              <p className="truncate text-xs text-muted-foreground">{user.email}</p>
            </div>
            <Button variant="ghost" size="icon" onClick={logout} aria-label="Sign out" title="Sign out">
              <LogOut />
            </Button>
          </div>
        )}
      </div>
    </header>
  );
}
