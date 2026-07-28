package com.alves_dev.sos.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
@OpenAPIDefinition(
        info = @Info(
                title = "SOS - Simple Object Storage API",
                version = "v2",
                description = "SOS V2 API. JSON operations use client API keys; content URLs remain public or access-key/token protected."
        ),
        tags = {
                @Tag(name = "Public content", description = "Public file delivery endpoints. Private files require an access key or temporary token."),
                @Tag(name = "Protected files", description = "File management endpoints protected by X-API-Key."),
                @Tag(name = "Protected buckets", description = "Bucket and bucket file endpoints protected by X-API-Key.")
        }
)
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "Client API key required by protected /api/v2 endpoints."
)
@SecurityScheme(
        name = "accessKey",
        type = SecuritySchemeType.APIKEY,
        in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.QUERY,
        paramName = "key",
        description = "Permanent access key query parameter for private content."
)
public class OpenApiConfig {
}
