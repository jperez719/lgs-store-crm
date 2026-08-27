package com.jperez.apitests;

import com.jperez.apitests.config.TestConfig;
import com.jperez.apitests.support.SelfContainedEnvironment;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseApiTest {

    @BeforeAll
    static void configureBaseUri() {
        boolean selfContained = Boolean.parseBoolean(
                System.getProperty("selfContained", "false")
        );

        String baseUrl = selfContained
                ? SelfContainedEnvironment.start()
                : TestConfig.resolveBaseUrl();

        RestAssured.baseURI = baseUrl;
        RestAssured.filters(new AllureRestAssured());
    }
}
