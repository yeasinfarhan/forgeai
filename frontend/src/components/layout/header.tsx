import { Menu } from "lucide-react";
import { useMatches } from "react-router";

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

      <div className="ml-auto">
        <ApiStatusIndicator state={health.state} />
      </div>
    </header>
  );
}
