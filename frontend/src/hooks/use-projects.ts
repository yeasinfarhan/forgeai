import { useCallback, useEffect, useState } from "react";

import { errorMessage, isAbortError, listProjects, type Project } from "@/lib/api";

type ProjectsState =
  | { kind: "loading" }
  | { kind: "loaded"; projects: Project[] }
  | { kind: "error"; message: string };

/** Loads the signed-in user's projects, most recently updated first. */
export function useProjects() {
  const [state, setState] = useState<ProjectsState>({ kind: "loading" });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setState({ kind: "loading" });
    listProjects(controller.signal)
      .then((projects) => setState({ kind: "loaded", projects }))
      .catch((error: unknown) => {
        if (isAbortError(error) || controller.signal.aborted) return;
        setState({ kind: "error", message: errorMessage(error) });
      });
    return () => controller.abort();
  }, [reloadKey]);

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  return { state, reload };
}
