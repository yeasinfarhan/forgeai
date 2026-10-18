import { lazy, Suspense, type ComponentType } from "react";

/**
 * A page whose code is downloaded the first time it is shown (a separate bundle chunk), with a
 * loading placeholder in the meantime.
 */
export function lazyPage(load: () => Promise<ComponentType>) {
  const Page = lazy(() => load().then((component) => ({ default: component })));
  return function LazyPage() {
    return (
      <Suspense fallback={<PageLoading />}>
        <Page />
      </Suspense>
    );
  };
}

function PageLoading() {
  return (
    <div role="status" aria-label="Loading page" className="h-96 animate-pulse rounded-xl border bg-card" />
  );
}
