package com.forgeai.support;

/**
 * Approved earlier stages for tests of later ones: a minimal architecture, data model and API
 * (customers and invoices), as the AI provider would return them.
 */
public final class DesignFixtures {

    private DesignFixtures() {
    }

    public static final String ARCHITECTURE = """
            {"overview": "A web app and a REST API on PostgreSQL.", "style": "Client + REST API + database",
             "styleRationale": "Small team.",
             "components": [
               {"name": "API", "kind": "SERVICE", "responsibility": "Business rules.", "technology": "Spring Boot",
                "dependsOn": [], "requirements": ["FR-001"]},
               {"name": "Web app", "kind": "CLIENT", "responsibility": "Screens.", "technology": "React",
                "dependsOn": ["API"], "requirements": ["FR-001"]}],
             "decisions": [], "flows": []}
            """;

    public static final String DATABASE = """
            {"summary": "Customers receive invoices.", "entities": [
              {"name": "customers", "description": "", "indexes": [], "requirements": [], "columns": [
                {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [], "description": ""}]},
              {"name": "invoices", "description": "", "indexes": [], "requirements": ["FR-001"], "columns": [
                {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [], "description": ""},
                {"name": "customer_id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": false, "unique": false, "defaultValue": null,
                 "references": {"entity": "customers", "column": "id", "onDelete": "RESTRICT"}, "allowedValues": [], "description": ""}]}
            ]}
            """;

    public static final String API = """
            {"summary": "Invoices API.", "basePath": "/api", "authentication": "Bearer token.", "errorFormat": "Problem details.",
             "errors": [{"status": 404, "code": "not_found", "description": "Missing."}],
             "schemas": [{"name": "InvoiceResponse", "description": "", "entity": "invoices", "fields": [
               {"name": "id", "type": "UUID", "ref": null, "array": false, "required": true, "description": ""}]}],
             "endpoints": [
               {"method": "GET", "path": "/invoices", "summary": "List invoices", "description": "", "authenticated": true,
                "entity": "invoices", "requestSchema": null, "responseSchema": "InvoiceResponse", "responseList": true,
                "successStatus": 200, "pathParameters": [], "queryParameters": [], "errorStatuses": [], "requirements": ["FR-001"]},
               {"method": "POST", "path": "/invoices", "summary": "Create an invoice", "description": "", "authenticated": true,
                "entity": "invoices", "requestSchema": "InvoiceResponse", "responseSchema": "InvoiceResponse", "responseList": false,
                "successStatus": 201, "pathParameters": [], "queryParameters": [], "errorStatuses": [], "requirements": ["FR-002"]}]}
            """;
}
