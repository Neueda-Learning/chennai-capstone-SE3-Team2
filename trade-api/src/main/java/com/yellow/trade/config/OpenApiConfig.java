package com.yellow.trade.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1.0 and Swagger UI configuration for the Trade API.
 * 
 * Configures:
 * - API metadata (title, version, description, contact, license)
 * - JWT Bearer token security scheme for endpoint authentication
 * 
 * Generated OpenAPI specification available at:
 * - JSON: /v3/api-docs
 * - YAML: /v3/api-docs.yaml
 * - Interactive Swagger UI: /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Trade REST API")
                .version("1.0.0")
                .description("REST API for placing and managing orders in the trading platform. " +
                    "All endpoints require JWT authentication from the auth-service.")
                .contact(new Contact()
                    .name("Yellow Trading Platform")
                    .url("https://github.com/bangalore-capstone/")
                    .email("support@yellow.trade"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://github.com/bangalore-capstone/")))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                    .type(Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token issued by auth-service. " +
                        "Token must include: issuer='auth-service', accountId claim, " +
                        "and valid signature (HS256 algorithm).")));
    }
}
