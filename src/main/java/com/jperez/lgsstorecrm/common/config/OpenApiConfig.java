package com.jperez.lgsstorecrm.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storeCrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Store CRM API")
                        .description("Manages customer store credit and transaction history")
                        .version("v0.0.1"));
    }
}
