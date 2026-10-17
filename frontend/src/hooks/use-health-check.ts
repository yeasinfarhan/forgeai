import { useCallback, useEffect, useRef, useState } from "react";

import { fetchHealth, type HealthResponse } from "@/lib/api";

export type HealthState =
  | { kind: "checking" }
  | { kind: "up"; data: HealthResponse }
  | { kind: "down"; message: string };

export interface HealthCheck {
  state: HealthState;
  checkedAt: Date | null;
  latencyMs: number | null;
  isRefreshing: boolean;
  refresh: () => void;
}

const POLL_INTERVAL_MS = 30_000;

/** Polls GET /api/health and exposes the result, latency and a manual refresh. */
export function useHealthCheck(): HealthCheck {
  const [state, setState] = useState<HealthState>({ kind: "checking" });
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);
  const [latencyMs, setLatencyMs] = useState<number | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const inFlight = useRef<AbortController | null>(null);

  const runCheck = useCallback(async () => {
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;
    setIsRefreshing(true);

    const startedAt = performance.now();
    try {
      const data = await fetchHealth(controller.signal);
      setState(data.status === "UP" ? { kind: "up", data } : { kind: "down", message: `Status ${data.status}` });
    } catch (error) {
      if (controller.signal.aborted) return;
      const message = error instanceof Error ? error.message : "Unknown error";
      setState({ kind: "down", message });
    }

    setLatencyMs(Math.round(performance.now() - startedAt));
    setCheckedAt(new Date());
    setIsRefreshing(false);
  }, []);

  useEffect(() => {
    void runCheck();
    const interval = window.setInterval(() => void runCheck(), POLL_INTERVAL_MS);
    return () => {
      window.clearInterval(interval);
      inFlight.current?.abort();
    };
  }, [runCheck]);

  return { state, checkedAt, latencyMs, isRefreshing, refresh: () => void runCheck() };
}
