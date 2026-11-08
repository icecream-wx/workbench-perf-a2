package com.example.perfa2.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI applicationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("perf-a2 API")
                .version("v1")
                .description("perf-a2 application scaffold"));
    }
}
