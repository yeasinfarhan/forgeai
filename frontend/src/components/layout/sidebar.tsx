import { FolderKanban, LayoutDashboard, type LucideIcon } from "lucide-react";
import { NavLink } from "react-router";

import { BrandMark } from "@/components/layout/brand-mark";
import { cn } from "@/lib/utils";

interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
  /** Only highlight on an exact match (the dashboard would otherwise match every route). */
  end?: boolean;
}

// Only routes that exist are listed. New sections are added as their phases ship.
const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", to: "/", icon: LayoutDashboard, end: true },
  { label: "Projects", to: "/projects", icon: FolderKanban },
];

interface SidebarProps {
  isMobileOpen: boolean;
  onNavigate: () => void;
}

export function Sidebar({ isMobileOpen, onNavigate }: SidebarProps) {
  return (
    <>
      {/* Backdrop for the mobile drawer */}
      <div
        aria-hidden="true"
        onClick={onNavigate}
        className={cn(
          "fixed inset-0 z-30 bg-black/60 backdrop-blur-sm transition-opacity md:hidden",
          isMobileOpen ? "opacity-100" : "pointer-events-none opacity-0",
        )}
      />

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-transform md:sticky md:top-0 md:h-svh md:translate-x-0",
          isMobileOpen ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex h-14 items-center gap-2.5 border-b border-sidebar-border px-5">
          <BrandMark />
          <span className="text-[15px] font-semibold tracking-tight">ForgeAI</span>
        </div>

        <nav aria-label="Main" className="flex-1 space-y-6 px-3 py-5">
          <div>
            <p className="px-2 pb-2 text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
              Workspace
            </p>
            <ul className="space-y-0.5">
              {NAV_ITEMS.map(({ label, to, icon: Icon, end }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    end={end}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors",
                        isActive
                          ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                          : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
                      )
                    }
                  >
                    <Icon className="size-4" />
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        </nav>

        <div className="border-t border-sidebar-border px-5 py-4">
          <p className="text-xs font-medium">Phase 5 · Architecture</p>
          <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">v0.5.0</p>
        </div>
      </aside>
    </>
  );
}
