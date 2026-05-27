package com.shashikiranreddy.splitwise.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata for the Swagger UI.
 *
 * <p>Declares a single bearer-JWT security scheme so that the "Authorize"
 * button at the top of Swagger UI lets the user paste their token and
 * have it sent on subsequent requests automatically.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Splitwise-Lite API",
                version = "0.1.0",
                description = """
                        REST API for tracking shared group expenses with a debt-simplification
                        algorithm that reduces who-owes-whom into the minimum number of cash transfers.

                        ### Quick start
                        1. Call **POST /auth/register** to create a user; copy the `accessToken`.
                        2. Click the **Authorize** button (top right) and paste the token.
                        3. Try **POST /groups**, **POST /groups/{id}/expenses**, then **GET /groups/{id}/settle-up**.
                        """,
                contact = @Contact(name = "Shashikiran Reddy Pagilla",
                        url = "https://github.com/shashi1349/kiro/tree/main/splitwise-lite"),
                license = @License(name = "MIT")
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the JWT obtained from /auth/register or /auth/login."
)
public class OpenApiConfig {
}
