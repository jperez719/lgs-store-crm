package com.jperez.apitests;

import org.junit.jupiter.api.Test;

import static com.jperez.apitests.support.ApiFixtures.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class TenantIsolationApiTest extends BaseApiTest {

    @Test
    void customerFromTenantA_isNotAccessibleViaTenantB() {
        String tenantAId = createTenant("Tenant A");
        String tenantBId = createTenant("Tenant B");
        String customerIdInA = createCustomer(tenantAId, "Alice", "Isolation");

        given()
                .when()
                .get("/api/tenants/{tenantId}/customers/{customerId}", tenantAId, customerIdInA)
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/api/tenants/{tenantId}/customers/{customerId}", tenantBId, customerIdInA)
                .then()
                .statusCode(404)
                .body("error", equalTo("Not Found"));
    }

    @Test
    void listingCustomers_underTenantB_neverIncludesTenantAsCustomers() {
        String tenantAId = createTenant("Tenant A List");
        String tenantBId = createTenant("Tenant B List");
        String customerIdInA = createCustomer(tenantAId, "Bob", "Isolation");

        given()
                .queryParam("size", 100)
                .when()
                .get("/api/tenants/{tenantId}/customers", tenantBId)
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(customerIdInA)));
    }
}
