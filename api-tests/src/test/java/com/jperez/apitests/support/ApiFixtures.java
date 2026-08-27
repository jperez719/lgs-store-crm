package com.jperez.apitests.support;

import static io.restassured.RestAssured.given;

public class ApiFixtures {

    public static String createTenant(String namePrefix) {
        String name = namePrefix + " " + System.currentTimeMillis();
        return given()
                .contentType("application/json")
                .body("{ \"name\": \"" + name + "\" }")
                .when()
                .post("/api/tenants")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    public static String createCustomer(String tenantId, String firstName, String lastName) {
        String body = String.format("""
                {
                    "firstName": "%s",
                    "lastName": "%s",
                    "phoneNumber": "555-1234",
                    "address": "123 Main St"
                }
                """, firstName, lastName);

        return given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/tenants/{tenantId}/customers", tenantId)
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    public static String createEmployee(String tenantId, String firstName, String lastName) {
        String body = String.format("""
                {
                    "firstName": "%s",
                    "lastName": "%s",
                    "role": "EMPLOYEE"
                }
                """, firstName, lastName);

        return given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/tenants/{tenantId}/employees", tenantId)
                .then()
                .statusCode(201)
                .extract().path("id");
    }
}
