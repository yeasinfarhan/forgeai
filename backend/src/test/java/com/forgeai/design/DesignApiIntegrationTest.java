package com.forgeai.design;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiResult;
import com.forgeai.support.IntegrationTest;

/**
 * Database and API design end to end, with the AI provider mocked: prerequisites, repair of model
 * output, SQL export (run against the test PostgreSQL), OpenAPI export, editing rules, workflow
 * and the consistency checks between requirements, data model and API.
 */
class DesignApiIntegrationTest extends IntegrationTest {

    private static final String ARCHITECTURE = """
            {"overview": "A web app and a REST API on PostgreSQL.", "style": "Client + REST API + database",
             "styleRationale": "Small team.",
             "components": [{"name": "API", "kind": "SERVICE", "responsibility": "Business rules.", "technology": "Spring Boot",
                             "dependsOn": [], "requirements": ["FR-001"]}],
             "decisions": [], "flows": []}
            """;

    /** Deliberately imperfect: names to clean, a duplicate, unknown references, missing type parameters. */
    private static final String DATABASE = """
            {"summary": "Customers receive invoices.",
             "entities": [
               {"name": "Customers", "description": "People who receive invoices.", "indexes": [],
                "requirements": ["FR-001", "FR-999"],
                "columns": [
                  {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": true,
                   "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [],
                   "description": "Primary key"},
                  {"name": "email", "type": "VARCHAR", "length": 320, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": false, "unique": true, "defaultValue": null, "references": null, "allowedValues": [],
                   "description": "Login and contact address"},
                  {"name": "createdAt", "type": "TIMESTAMP", "length": null, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": false, "unique": false, "defaultValue": "now", "references": null, "allowedValues": [],
                   "description": ""}]},
               {"name": "invoices", "description": "Bills sent to customers.", "requirements": ["fr-002"],
                "indexes": [{"columns": ["customer_id", "nope"], "unique": false}, {"columns": ["nope"], "unique": true}],
                "columns": [
                  {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [],
                   "description": ""},
                  {"name": "customer_id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": false, "unique": false, "defaultValue": null,
                   "references": {"entity": "customers", "column": "id", "onDelete": "SET_NULL"}, "allowedValues": [],
                   "description": "Who the invoice is for"},
                  {"name": "status", "type": "VARCHAR", "length": 20, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": false, "unique": false, "defaultValue": "DRAFT", "references": null,
                   "allowedValues": ["DRAFT", "SENT", "PAID"], "description": "Customer's view; it's tracked"},
                  {"name": "total", "type": "DECIMAL", "length": null, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": false, "unique": false, "defaultValue": "0", "references": null, "allowedValues": [],
                   "description": ""},
                  {"name": "notes", "type": "VARCHAR", "length": null, "precision": null, "scale": null, "nullable": true,
                   "primaryKey": false, "unique": false, "defaultValue": null, "references": null, "allowedValues": [],
                   "description": ""},
                  {"name": "ghost_id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": true,
                   "primaryKey": false, "unique": false, "defaultValue": null,
                   "references": {"entity": "ghosts", "column": "id", "onDelete": "CASCADE"}, "allowedValues": [],
                   "description": ""}]},
               {"name": "customers", "description": "Duplicate", "indexes": [], "requirements": [],
                "columns": [{"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                   "primaryKey": true, "unique": false, "defaultValue": null, "references": null, "allowedValues": [],
                   "description": ""}]}
             ]}
            """;

    /** Deliberately imperfect: a relative base path, statuses outside the error model, unknown schemas, a duplicate route. */
    private static final String API = """
            {"summary": "REST API for invoices.", "basePath": "api/", "authentication": "Bearer token from POST /auth/login.",
             "errorFormat": "RFC 9457 problem details.",
             "errors": [{"status": 404, "code": "not_found", "description": "No such resource."},
                        {"status": 400, "code": "validation_failed", "description": "Invalid input."},
                        {"status": 404, "code": "duplicate", "description": "Duplicate."},
                        {"status": 200, "code": "ok", "description": "Not an error."}],
             "schemas": [
               {"name": "invoice_response", "description": "An invoice.", "entity": "invoices", "fields": [
                 {"name": "id", "type": "UUID", "ref": null, "array": false, "required": true, "description": ""},
                 {"name": "customerId", "type": "UUID", "ref": null, "array": false, "required": true, "description": ""},
                 {"name": "status", "type": "STRING", "ref": null, "array": false, "required": true, "description": ""},
                 {"name": "dueDate", "type": "DATE", "ref": null, "array": false, "required": false, "description": ""},
                 {"name": "lines", "type": "OBJECT", "ref": "LineResponse", "array": true, "required": false, "description": ""}]},
               {"name": "CreateInvoiceRequest", "description": "", "entity": "Invoices", "fields": [
                 {"name": "customerId", "type": "UUID", "ref": null, "array": false, "required": true, "description": ""}]},
               {"name": "Orphan", "description": "", "entity": "nonexistent", "fields": [
                 {"name": "x", "type": "STRING", "ref": null, "array": false, "required": false, "description": ""}]}
             ],
             "endpoints": [
               {"method": "GET", "path": "/invoices", "summary": "List invoices", "description": "", "authenticated": true,
                "entity": "invoices", "requestSchema": "CreateInvoiceRequest", "responseSchema": "InvoiceResponse",
                "responseList": true, "successStatus": 200, "pathParameters": [],
                "queryParameters": [{"name": "status", "type": "STRING", "required": false, "description": "Filter"}],
                "errorStatuses": [400, 500], "requirements": ["FR-001"]},
               {"method": "POST", "path": "/invoices", "summary": "Create an invoice", "description": "", "authenticated": true,
                "entity": "invoices", "requestSchema": "CreateInvoiceRequest", "responseSchema": "InvoiceResponse",
                "responseList": false, "successStatus": null, "pathParameters": [], "queryParameters": [],
                "errorStatuses": [400], "requirements": ["FR-002"]},
               {"method": "GET", "path": "/invoices/{invoiceId}", "summary": "Get an invoice", "description": "",
                "authenticated": true, "entity": "invoices", "requestSchema": null, "responseSchema": "InvoiceResponse",
                "responseList": false, "successStatus": 200, "pathParameters": [], "queryParameters": [],
                "errorStatuses": [404], "requirements": []},
               {"method": "DELETE", "path": "/invoices/{id}/", "summary": "Delete an invoice", "description": "",
                "authenticated": true, "entity": "invoices", "requestSchema": null, "responseSchema": "InvoiceResponse",
                "responseList": false, "successStatus": 204,
                "pathParameters": [{"name": "id", "type": "UUID", "required": true, "description": "Invoice id"}],
                "queryParameters": [], "errorStatuses": [404], "requirements": []},
               {"method": "GET", "path": "/invoices/{other}", "summary": "Same route again", "description": "",
                "authenticated": true, "entity": null, "requestSchema": null, "responseSchema": null, "responseList": false,
                "successStatus": 200, "pathParameters": [], "queryParameters": [], "errorStatuses": [], "requirements": []},
               {"method": "FETCH", "path": "/x", "summary": "Not a method", "description": "", "authenticated": true,
                "entity": null, "requestSchema": null, "responseSchema": null, "responseList": false, "successStatus": 200,
                "pathParameters": [], "queryParameters": [], "errorStatuses": [], "requirements": []}
             ]}
            """;

    /** A valid hand-written data model: one customer has many invoices. */
    private static final String DATABASE_EDIT = """
            {"version": %d, "content": {"summary": "Edited.", "entities": [
              {"name": "customers", "description": null, "indexes": [], "requirements": ["FR-001"], "columns": [
                {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": %s, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [], "description": null}]},
              {"name": "invoices", "description": null, "indexes": [{"columns": ["customer_id"], "unique": false}],
               "requirements": ["FR-002"], "columns": [
                {"name": "id", "type": "UUID", "length": null, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": true, "unique": false, "defaultValue": "random", "references": null, "allowedValues": [], "description": null},
                {"name": "customer_id", "type": "%s", "length": %s, "precision": null, "scale": null, "nullable": false,
                 "primaryKey": false, "unique": false, "defaultValue": null,
                 "references": {"entity": "customers", "column": "%s", "onDelete": "CASCADE"}, "allowedValues": [], "description": null}]}
            ]}}
            """;

    @MockitoBean
    private AiClient ai;

    @Autowired
    private DataSource dataSource;

    private String token;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        projectId = createProject(token, "Invoice Tracker");
    }

    @Test
    void eachStageNeedsTheApprovedStageBefore() throws Exception {
        approvedRequirements();
        perform(get("/api/projects/{id}/database-design", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.design").value(nullValue()))
                .andExpect(jsonPath("$.canGenerate").value(false))
                .andExpect(jsonPath("$.blocker").value("Generate and approve the architecture first."));
        perform(post("/api/projects/{id}/database-design/generate", projectId))
                .andExpect(status().isConflict());
        perform(post("/api/projects/{id}/api-design/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Approve the architecture first."));
        perform(get("/api/projects/{id}/database-design/export.sql", projectId)).andExpect(status().isConflict());

        approvedArchitecture();
        perform(get("/api/projects/{id}/database-design", projectId)).andExpect(jsonPath("$.canGenerate").value(true));
        perform(post("/api/projects/{id}/api-design/generate", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Generate and approve the database design first."));
    }

    @Test
    void generatesARepairedDataModelAndExportsWorkingSql() throws Exception {
        approvedRequirements();
        approvedArchitecture();
        when(ai.complete(any())).thenReturn(reply(DATABASE));

        perform(post("/api/projects/{id}/database-design/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.design.status").value("DRAFT"))
                .andExpect(jsonPath("$.design.revision").value(1))
                .andExpect(jsonPath("$.design.inputsChanged").value(false))
                .andExpect(jsonPath("$.canGenerate").value(true))
                .andExpect(jsonPath("$.design.content.entities.length()").value(2)) // duplicate dropped
                .andExpect(jsonPath("$.design.content.entities[0].name").value("customers"))
                .andExpect(jsonPath("$.design.content.entities[0].requirements.length()").value(1)) // FR-999 dropped
                .andExpect(jsonPath("$.design.content.entities[0].columns[0].nullable").value(false)) // primary key
                .andExpect(jsonPath("$.design.content.entities[0].columns[2].name").value("created_at"))
                .andExpect(jsonPath("$.design.content.entities[1].requirements[0]").value("FR-002"))
                .andExpect(jsonPath("$.design.content.entities[1].columns[1].references.onDelete").value("RESTRICT"))
                .andExpect(jsonPath("$.design.content.entities[1].columns[3].precision").value(19))
                .andExpect(jsonPath("$.design.content.entities[1].columns[3].scale").value(4))
                .andExpect(jsonPath("$.design.content.entities[1].columns[4].type").value("TEXT"))
                .andExpect(jsonPath("$.design.content.entities[1].columns[5].references").value(nullValue()))
                .andExpect(jsonPath("$.design.content.entities[1].indexes.length()").value(1))
                .andExpect(jsonPath("$.design.content.entities[1].indexes[0].columns.length()").value(1))
                .andExpect(jsonPath("$.findings.length()").value(0));

        String sql = perform(get("/api/projects/{id}/database-design/export.sql", projectId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-tracker-schema.sql")))
                .andReturn().getResponse().getContentAsString();
        assertThat(sql)
                .contains("CREATE TABLE \"customers\"")
                .contains("\"id\" UUID NOT NULL DEFAULT gen_random_uuid()")
                .contains("\"total\" NUMERIC(19, 4) NOT NULL DEFAULT 0")
                .contains("CHECK (\"status\" IN ('DRAFT', 'SENT', 'PAID'))")
                .contains("COMMENT ON COLUMN \"invoices\".\"status\" IS 'Customer''s view; it''s tracked';")
                .contains("FOREIGN KEY (\"customer_id\") REFERENCES \"customers\" (\"id\") ON DELETE RESTRICT")
                .contains("CREATE INDEX \"ix_invoices_customer_id\" ON \"invoices\" (\"customer_id\");");

        // The script must run on PostgreSQL: execute it in a fresh schema and look at the result.
        String schema = "export_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema);
            try {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes()));
                try (ResultSet tables = statement.executeQuery(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = '" + schema + "'")) {
                    tables.next();
                    assertThat(tables.getInt(1)).isEqualTo(2);
                }
                statement.execute("INSERT INTO customers (email) VALUES ('a@example.com')");
                statement.execute("INSERT INTO invoices (customer_id) SELECT id FROM customers");
                try (ResultSet invoice = statement.executeQuery("SELECT status, total FROM invoices")) {
                    invoice.next();
                    assertThat(invoice.getString(1)).isEqualTo("DRAFT");
                    assertThat(invoice.getBigDecimal(2)).isZero();
                }
            } finally {
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void dataModelEditsAreValidatedAndReviewed() throws Exception {
        approvedRequirements();
        approvedArchitecture();
        when(ai.complete(any())).thenReturn(reply(DATABASE));
        perform(post("/api/projects/{id}/database-design/generate", projectId)).andExpect(status().isOk());

        // Broken references block saving
        editDatabase(DATABASE_EDIT.formatted(0, "true", "UUID", "null", "missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("invoices.customer_id references customers.missing, which is not a column."));
        editDatabase(DATABASE_EDIT.formatted(0, "true", "VARCHAR", "null", "id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("invoices.customer_id is VARCHAR and needs a length."));
        editDatabase(DATABASE_EDIT.formatted(0, "true", "UUID", "null", "id").replace("\"customers\", \"description\"", "\"Bad Name\", \"description\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("content.entities[0].name"));

        // Design issues are saved but reported
        editDatabase(DATABASE_EDIT.formatted(0, "false", "VARCHAR", "36", "id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.design.edited").value(true))
                .andExpect(jsonPath("$.design.revision").value(2))
                .andExpect(jsonPath("$.findings[*].message", hasItem("customers has no primary key.")))
                .andExpect(jsonPath("$.findings[*].message", hasItem(
                        "invoices.customer_id is VARCHAR but references customers.id, which is UUID.")))
                .andExpect(jsonPath("$.findings[*].message", hasItem(
                        "invoices.customer_id references customers.id, which is neither the primary key nor unique.")));
        editDatabase(DATABASE_EDIT.formatted(1, "true", "UUID", "null", "id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings.length()").value(0));
        editDatabase(DATABASE_EDIT.formatted(1, "true", "UUID", "null", "id")).andExpect(status().isConflict()); // stale

        // Review workflow: approval needs the approved architecture
        changeStatus("database-design", "IN_REVIEW", 2).andExpect(status().isOk());
        perform(post("/api/projects/{id}/architecture/status", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DRAFT\", \"version\": 2}"))
                .andExpect(status().isOk());
        changeStatus("database-design", "APPROVED", 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Approve the architecture before approving the database design."));
        perform(get("/api/projects/{id}/database-design", projectId))
                .andExpect(jsonPath("$.canGenerate").value(false));

        perform(get("/api/projects/{id}/database-design/revisions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].change").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[3].change").value("GENERATED"));
        perform(get("/api/projects/{id}/database-design/revisions/1", projectId))
                .andExpect(jsonPath("$.content.summary").value("Customers receive invoices."));
    }

    @Test
    void generatesARepairedApiExportsOpenApiAndChecksConsistency() throws Exception {
        approvedRequirements();
        approvedArchitecture();
        when(ai.complete(any())).thenReturn(reply(DATABASE), reply(API));
        perform(post("/api/projects/{id}/database-design/generate", projectId)).andExpect(status().isOk());
        changeStatus("database-design", "IN_REVIEW", 0).andExpect(status().isOk());
        changeStatus("database-design", "APPROVED", 1).andExpect(status().isOk());

        perform(post("/api/projects/{id}/api-design/generate", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.design.status").value("DRAFT"))
                .andExpect(jsonPath("$.database.entities.length()").value(2))
                .andExpect(jsonPath("$.design.content.basePath").value("/api"))
                .andExpect(jsonPath("$.design.content.errors.length()").value(2))
                .andExpect(jsonPath("$.design.content.errors[0].status").value(400))
                .andExpect(jsonPath("$.design.content.schemas.length()").value(3))
                .andExpect(jsonPath("$.design.content.schemas[0].name").value("InvoiceResponse"))
                .andExpect(jsonPath("$.design.content.schemas[0].fields.length()").value(4)) // unknown nested schema dropped
                .andExpect(jsonPath("$.design.content.schemas[1].entity").value("invoices"))
                .andExpect(jsonPath("$.design.content.schemas[2].entity").value(nullValue()))
                .andExpect(jsonPath("$.design.content.endpoints.length()").value(4))
                .andExpect(jsonPath("$.design.content.endpoints[0].requestSchema").value(nullValue()))
                .andExpect(jsonPath("$.design.content.endpoints[0].errorStatuses.length()").value(1))
                .andExpect(jsonPath("$.design.content.endpoints[1].successStatus").value(201))
                .andExpect(jsonPath("$.design.content.endpoints[2].pathParameters[0].name").value("invoiceId"))
                .andExpect(jsonPath("$.design.content.endpoints[2].pathParameters[0].type").value("UUID"))
                .andExpect(jsonPath("$.design.content.endpoints[3].path").value("/invoices/{id}"))
                .andExpect(jsonPath("$.design.content.endpoints[3].responseSchema").value(nullValue()))
                // consistency between requirements, data model and API
                .andExpect(jsonPath("$.findings[*].message", hasItem("InvoiceResponse.dueDate has no matching column in invoices.")))
                .andExpect(jsonPath("$.findings[*].message", hasItem("Schema Orphan is not used by any endpoint.")))
                .andExpect(jsonPath("$.findings[*].message", hasItem("No endpoint or schema works with the entity customers.")))
                .andExpect(jsonPath("$.findings[*].message", hasItem(
                        "FR-003 (Send payment reminders) is not served by any entity or endpoint.")))
                .andExpect(jsonPath("$.findings[*].message", not(hasItem(containsString("FR-001")))));

        perform(get("/api/projects/{id}/api-design/openapi.json", projectId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-tracker-openapi.json")))
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.info.title").value("Invoice Tracker API"))
                .andExpect(jsonPath("$.servers[0].url").value("/api"))
                .andExpect(jsonPath("$.paths['/invoices'].get.operationId").value("getInvoices"))
                .andExpect(jsonPath("$.paths['/invoices'].get.security[0].bearerAuth.length()").value(0))
                .andExpect(jsonPath("$.paths['/invoices'].get.parameters[0].in").value("query"))
                .andExpect(jsonPath("$.paths['/invoices'].get.responses['200'].content['application/json'].schema.type").value("array"))
                .andExpect(jsonPath("$.paths['/invoices'].get.responses['200'].content['application/json'].schema.items['$ref']")
                        .value("#/components/schemas/InvoiceResponse"))
                .andExpect(jsonPath("$.paths['/invoices'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/CreateInvoiceRequest"))
                .andExpect(jsonPath("$.paths['/invoices'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/invoices/{invoiceId}'].get.parameters[0].schema.format").value("uuid"))
                .andExpect(jsonPath("$.paths['/invoices/{invoiceId}'].get.responses['404'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/Problem"))
                .andExpect(jsonPath("$.paths['/invoices/{id}'].delete.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.InvoiceResponse.required.length()").value(3))
                .andExpect(jsonPath("$.components.schemas.Problem.properties.code.enum[0]").value("validation_failed"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void apiEditsAreValidatedAndFollowTheDataModel() throws Exception {
        approvedRequirements();
        approvedArchitecture();
        when(ai.complete(any())).thenReturn(reply(DATABASE), reply(API));
        perform(post("/api/projects/{id}/database-design/generate", projectId)).andExpect(status().isOk());
        changeStatus("database-design", "IN_REVIEW", 0).andExpect(status().isOk());
        changeStatus("database-design", "APPROVED", 1).andExpect(status().isOk());
        perform(post("/api/projects/{id}/api-design/generate", projectId)).andExpect(status().isOk());

        String valid = """
                {"version": 0, "content": {"summary": "Edited API.", "basePath": "/api", "authentication": "Bearer token.",
                 "errorFormat": "Problem details.", "errors": [{"status": 404, "code": "not_found", "description": "Missing."}],
                 "schemas": [{"name": "CustomerResponse", "description": null, "entity": "customers", "fields": [
                   {"name": "id", "type": "UUID", "ref": null, "array": false, "required": true, "description": null},
                   {"name": "email", "type": "EMAIL", "ref": null, "array": false, "required": true, "description": null}]}],
                 "endpoints": [{"method": "GET", "path": "/customers/{customerId}", "summary": "Get a customer",
                   "description": null, "authenticated": true, "entity": "customers", "requestSchema": null,
                   "responseSchema": "CustomerResponse", "responseList": false, "successStatus": 200,
                   "pathParameters": [{"name": "customerId", "type": "UUID", "required": true, "description": null}],
                   "queryParameters": [], "errorStatuses": [404], "requirements": ["FR-001"]}]}}
                """;
        editApi(valid.replace("\"errorStatuses\": [404]", "\"errorStatuses\": [409]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("GET /customers/{customerId} can return 409, which is not in the error model."));
        editApi(valid.replace("\"/customers/{customerId}\"", "\"/customers/{id}\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("GET /customers/{id}: the path parameter {id} needs a definition."));
        editApi(valid.replace("\"responseSchema\": \"CustomerResponse\"", "\"responseSchema\": \"Nope\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("GET /customers/{customerId} uses Nope as response body, which is not a schema."));

        editApi(valid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.design.edited").value(true))
                .andExpect(jsonPath("$.findings[*].message", hasItem("No endpoint or schema works with the entity invoices.")))
                .andExpect(jsonPath("$.findings[*].message", not(hasItem(containsString("CustomerResponse")))));

        // Approval needs the approved data model; a changed data model is flagged
        changeStatus("api-design", "IN_REVIEW", 1).andExpect(status().isOk());
        changeStatus("database-design", "DRAFT", 2).andExpect(status().isOk());
        changeStatus("api-design", "APPROVED", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Approve the database design before approving the API design."));
        editDatabase(DATABASE_EDIT.formatted(3, "true", "UUID", "null", "id")).andExpect(status().isOk());
        perform(get("/api/projects/{id}/api-design", projectId))
                .andExpect(jsonPath("$.design.inputsChanged").value(true))
                .andExpect(jsonPath("$.canGenerate").value(false));

        String otherToken = registerUser();
        mockMvc.perform(get("/api/projects/{id}/api-design", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{id}/database-design/export.sql", projectId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---------------------------------------------------------------

    private static AiResult reply(String json) {
        return new AiResult(json, "test-model", 100, 50);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions editDatabase(String body) throws Exception {
        return perform(put("/api/projects/{id}/database-design", projectId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions editApi(String body) throws Exception {
        return perform(put("/api/projects/{id}/api-design", projectId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeStatus(String stage, String target, int version) throws Exception {
        return perform(post("/api/projects/{id}/" + stage + "/status", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"%s\", \"version\": %d}".formatted(target, version)));
    }

    /** FR-001, FR-002, FR-003 and NFR-001, all approved. */
    private void approvedRequirements() throws Exception {
        createRequirement("FUNCTIONAL", "Manage customers");
        createRequirement("FUNCTIONAL", "Create an invoice");
        createRequirement("FUNCTIONAL", "Send payment reminders");
        createRequirement("NON_FUNCTIONAL", "Protect account data");
        for (String[] move : new String[][] {{"DRAFT", "IN_REVIEW"}, {"IN_REVIEW", "APPROVED"}}) {
            perform(post("/api/projects/{id}/requirements/bulk-status", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"from\": \"%s\", \"to\": \"%s\"}".formatted(move[0], move[1])))
                    .andExpect(status().isOk());
        }
    }

    private void createRequirement(String type, String title) throws Exception {
        perform(post("/api/projects/{id}/requirements", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "%s", "category": "General", "title": "%s", "description": "%s.",
                                 "acceptanceCriteria": ["It works"], "priority": "MUST"}
                                """.formatted(type, title, title)))
                .andExpect(status().isCreated());
    }

    /** Architecture generated (AI mocked) and approved: versions 0 → 1 → 2. */
    private void approvedArchitecture() throws Exception {
        when(ai.complete(any())).thenReturn(reply(ARCHITECTURE));
        perform(post("/api/projects/{id}/architecture/generate", projectId)).andExpect(status().isOk());
        for (String[] move : new String[][] {{"IN_REVIEW", "0"}, {"APPROVED", "1"}}) {
            perform(post("/api/projects/{id}/architecture/status", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"%s\", \"version\": %s}".formatted(move[0], move[1])))
                    .andExpect(status().isOk());
        }
    }
}
