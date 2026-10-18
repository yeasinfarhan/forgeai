package com.forgeai.design.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiAssistantTest {

    @Test
    void namesAreNormalised() {
        assertThat(ApiAssistant.schemaName("invoice_response")).isEqualTo("InvoiceResponse");
        assertThat(ApiAssistant.schemaName("123")).isNull();
        assertThat(ApiAssistant.fieldName("Due Date")).isEqualTo("dueDate");
        assertThat(ApiAssistant.fieldName("customer_id")).isEqualTo("customer_id");
        assertThat(ApiRules.normalisePath("invoices/")).isEqualTo("/invoices");
        assertThat(ApiRules.snakeCase("customerId")).isEqualTo("customer_id");
    }

    @Test
    void operationIdsFollowThePath() {
        var used = new java.util.HashSet<String>();
        var endpoint = new ApiDesignContent.Endpoint(HttpMethod.GET, "/invoices/{invoiceId}/lines", "s", null, true, null, null,
                null, false, 200, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        assertThat(OpenApiExporter.operationId(endpoint, used)).isEqualTo("getInvoicesByInvoiceIdLines");
        assertThat(OpenApiExporter.operationId(endpoint, used)).isEqualTo("getInvoicesByInvoiceIdLines2");
    }
}
