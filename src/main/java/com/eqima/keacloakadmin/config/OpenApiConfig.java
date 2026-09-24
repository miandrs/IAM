package com.eqima.keacloakadmin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${swagger.server-url}")
    private String serverUrl;

    @Value("${swagger.local-url}")
    private String localServerUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        Server server = new Server();
        server.setUrl(serverUrl); // <- injecte l'URL dynamique (http ou https)

        Server localServer = new Server();
        localServer.setUrl(localServerUrl);

        return new OpenAPI()
            .info(new Info()
                .title("Plateforme TSINJO API")
                .version("1.0"))
                    .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                    .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                            new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .servers(List.of(server, localServer)); // <- ajoute le serveur ici
    }
}
