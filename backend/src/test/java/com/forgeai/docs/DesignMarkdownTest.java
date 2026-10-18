package com.forgeai.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.forgeai.architecture.ArchitectureContent;
import com.forgeai.design.api.ApiDesignContent;
import com.forgeai.design.database.DatabaseDesignContent;
import com.forgeai.support.DesignFixtures;

import tools.jackson.databind.json.JsonMapper;

class DesignMarkdownTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void diagramsAreDerivedFromTheDesign() {
        ArchitectureContent architecture = json.readValue(DesignFixtures.ARCHITECTURE, ArchitectureContent.class);
        assertThat(DesignMarkdown.componentDiagram(architecture.components())).isEqualTo("""
                flowchart LR
                  c0["API<br/><small>Spring Boot</small>"]
                  c1(["Web app<br/><small>React</small>"])
                  c1 --> c0""");

        DatabaseDesignContent database = json.readValue(DesignFixtures.DATABASE, DatabaseDesignContent.class);
        assertThat(DesignMarkdown.erDiagram(database)).isEqualTo("""
                erDiagram
                  customers {
                    UUID id PK
                  }
                  invoices {
                    UUID id PK
                    UUID customer_id FK
                  }
                  customers ||--o{ invoices : "customer_id\"""");
        assertThat(DesignMarkdown.dataModel("Invoices", database))
                .contains("| `customer_id` | UUID | no |  |  | `customers.id` (on delete restrict) |  |");
    }

    @Test
    void apiReferenceLinksEndpointsAndSchemas() {
        ApiDesignContent api = json.readValue(DesignFixtures.API, ApiDesignContent.class);
        String md = DesignMarkdown.apiReference("Invoices", api);
        assertThat(md)
                .startsWith("# Invoices — API reference\n")
                .contains("| GET | [`/api/invoices`](#get-apiinvoices) | List invoices | required |")
                .contains("\n### POST /api/invoices\n")
                .contains("- **Request body:** [`InvoiceResponse`](#invoiceresponse)")
                .contains("- **Response:** 200 — list of [`InvoiceResponse`](#invoiceresponse)")
                .contains("| 404 | `not_found` | Missing. |")
                .contains("| `id` | uuid | yes |  |");
        assertThat(DesignMarkdown.anchor("GET /api/invoices/{invoiceId}")).isEqualTo("get-apiinvoicesinvoiceid");
        assertThat(DesignMarkdown.cell("a | b\nc")).isEqualTo("a \\| b c");
    }

    @Test
    void wrappingFencesAreRemovedFromWrittenDocuments() {
        assertThat(DocsAssistant.tidy("```markdown\n# Title\n\nText.\n```")).isEqualTo("# Title\n\nText.\n");
        assertThat(DocsAssistant.tidy("# Title\n\n```bash\nnpm test\n```")).isEqualTo("# Title\n\n```bash\nnpm test\n```\n");
        assertThat(DocsAssistant.tidy("  ")).isNull();
    }
}
