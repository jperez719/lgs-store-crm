package com.jperez.apitests;

import org.junit.jupiter.api.Test;

import static com.jperez.apitests.support.ApiFixtures.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class CreditTransactionLifecycleApiTest extends BaseApiTest {

    @Test
    void fullLifecycle_createCustomer_applyCredit_applyDebit_viewHistory() {
        String tenantId = createTenant("Lifecycle Store");
        String customerId = createCustomer(tenantId, "Jane", "Doe");
        String employeeId = createEmployee(tenantId, "John", "Smith");

        String creditBody = String.format("""
                {
                    "employeeId": "%s",
                    "type": "CREDIT",
                    "amount": 50.00,
                    "reason": "Refund"
                }
                """, employeeId);

        given()
                .contentType("application/json")
                .body(creditBody)
                .when()
                .post("/api/tenants/{tenantId}/customers/{customerId}/transactions", tenantId, customerId)
                .then()
                .statusCode(201)
                .body("storeCredit", equalTo(50.0f));

        String debitBody = String.format("""
                {
                    "employeeId": "%s",
                    "type": "DEBIT",
                    "amount": 20.00,
                    "reason": "Purchase"
                }
                """, employeeId);

        given()
                .contentType("application/json")
                .body(debitBody)
                .when()
                .post("/api/tenants/{tenantId}/customers/{customerId}/transactions", tenantId, customerId)
                .then()
                .statusCode(201)
                .body("storeCredit", equalTo(30.0f));

        given()
                .when()
                .get("/api/tenants/{tenantId}/customers/{customerId}", tenantId, customerId)
                .then()
                .statusCode(200)
                .body("storeCredit", equalTo(30.0f));

        given()
                .when()
                .get("/api/tenants/{tenantId}/customers/{customerId}/transactions", tenantId, customerId)
                .then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("content[0].type", equalTo("DEBIT"))
                .body("content[0].resultingBalance", equalTo(30.0f))
                .body("content[1].type", equalTo("CREDIT"))
                .body("content[1].resultingBalance", equalTo(50.0f));
    }

    @Test
    void debitExceedingBalance_returns409WithConflictError() {
        String tenantId = createTenant("Overdraw Store");
        String customerId = createCustomer(tenantId, "Jane", "Doe");
        String employeeId = createEmployee(tenantId, "John", "Smith");

        String debitBody = String.format("""
                {
                    "employeeId": "%s",
                    "type": "DEBIT",
                    "amount": 999.00,
                    "reason": "Overdraw attempt"
                }
                """, employeeId);

        given()
                .contentType("application/json")
                .body(debitBody)
                .when()
                .post("/api/tenants/{tenantId}/customers/{customerId}/transactions", tenantId, customerId)
                .then()
                .statusCode(409)
                .body("error", equalTo("Insufficient Credit"));
    }
}
