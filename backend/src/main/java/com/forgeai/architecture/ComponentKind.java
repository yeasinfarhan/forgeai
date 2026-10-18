package com.forgeai.architecture;

/** What kind of building block a component is; decides its shape in the component diagram. */
public enum ComponentKind {
    /** Something users interact with: web app, mobile app, CLI. */
    CLIENT,
    /** Server-side code: API, backend service, worker. */
    SERVICE,
    /** Database, cache, file or object storage. */
    DATA_STORE,
    /** A third-party system the project uses but does not build: payment provider, email service. */
    EXTERNAL,
    /** Supporting infrastructure: message broker, gateway, scheduler. */
    INFRASTRUCTURE
}
