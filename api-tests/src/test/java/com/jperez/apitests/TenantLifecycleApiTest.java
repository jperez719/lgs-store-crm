package com.jperez.apitests;

import org.junit.jupiter.api.Test;

import static com.jperez.apitests.support.ApiFixtures.createTenant;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class TenantLifecycleApiTest extends BaseApiTest {

    @Test
    void createAndRetrieveTenant() {
        String tenantId = createTenant("API Test Store");

        given()
                .when()
                .get("/api/tenants/{id}", tenantId)
                .then()
                .statusCode(200)
                .body("id", equalTo(tenantId))
                .body("name", notNullValue());
    }

    @Test
    void gettingNonexistentTenant_returns404() {
        given()
                .when()
                .get("/api/tenants/{id}", "00000000-0000-0000-0000-000000000099")
                .then()
                .statusCode(404)
                .body("error", equalTo("Not Found"));
    }
}
