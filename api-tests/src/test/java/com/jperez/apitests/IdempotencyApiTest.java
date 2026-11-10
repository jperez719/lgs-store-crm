package com.jperez.apitests;

import org.junit.jupiter.api.Test;

import static com.jperez.apitests.support.ApiFixtures.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class IdempotencyApiTest extends BaseApiTest {

    private io.restassured.response.ValidatableResponse postCredit(
            String tenantId, String customerId, String employeeId, String amount, String key) {
        String body = String.format("""
                { "employeeId": "%s", "type": "CREDIT", "amount": %s, "reason": "Retry test" }
                """, employeeId, amount);
        var request = given().contentType("application/json").body(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request.when()
                .post("/api/tenants/{tenantId}/customers/{customerId}/transactions", tenantId, customerId)
                .then();
    }

    @Test
    void sameKeyTwice_appliesOnlyOnce() {
        String tenantId = createTenant("Idem Store");
        String customerId = createCustomer(tenantId, "Jane", "Doe");
        String employeeId = createEmployee(tenantId, "John", "Smith");

        postCredit(tenantId, customerId, employeeId, "50.00", "retry-1")
                .statusCode(201).body("storeCredit", equalTo(50.0f));
        postCredit(tenantId, customerId, employeeId, "50.00", "retry-1")
                .statusCode(201).body("storeCredit", equalTo(50.0f));

        given().when()
                .get("/api/tenants/{tenantId}/customers/{customerId}/transactions", tenantId, customerId)
                .then().statusCode(200)
                .body("content", hasSize(1));
    }

    @Test
    void sameKeyWithDifferentAmount_returns422() {
        String tenantId = createTenant("Idem Mismatch Store");
        String customerId = createCustomer(tenantId, "Jane", "Doe");
        String employeeId = createEmployee(tenantId, "John", "Smith");

        postCredit(tenantId, customerId, employeeId, "50.00", "retry-2").statusCode(201);
        postCredit(tenantId, customerId, employeeId, "75.00", "retry-2")
                .statusCode(422)
                .body("error", notNullValue());
    }

    @Test
    void sameKeyInDifferentTenants_bothApply() {
        String tenantA = createTenant("Idem Store A");
        String customerA = createCustomer(tenantA, "Alice", "Anderson");
        String employeeA = createEmployee(tenantA, "Eve", "Adams");
        String tenantB = createTenant("Idem Store B");
        String customerB = createCustomer(tenantB, "Bob", "Brown");
        String employeeB = createEmployee(tenantB, "Ed", "Baker");

        postCredit(tenantA, customerA, employeeA, "10.00", "shared-key")
                .statusCode(201).body("storeCredit", equalTo(10.0f));
        postCredit(tenantB, customerB, employeeB, "10.00", "shared-key")
                .statusCode(201).body("storeCredit", equalTo(10.0f));
    }

    @Test
    void noKey_appliesEveryTime() {
        String tenantId = createTenant("Idem No Key Store");
        String customerId = createCustomer(tenantId, "Jane", "Doe");
        String employeeId = createEmployee(tenantId, "John", "Smith");

        postCredit(tenantId, customerId, employeeId, "50.00", null).statusCode(201);
        postCredit(tenantId, customerId, employeeId, "50.00", null)
                .statusCode(201).body("storeCredit", equalTo(100.0f));
    }
}
