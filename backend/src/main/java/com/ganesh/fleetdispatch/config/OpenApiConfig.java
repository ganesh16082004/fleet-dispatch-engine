package com.ganesh.fleetdispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI fleetDispatchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fleet Dispatch Engine API")
                        .description("Production-style real-time fleet dispatch and optimization platform API")
                        .version("v1")
                        .contact(new Contact().name("Fleet Dispatch Engine")));
    }
}
