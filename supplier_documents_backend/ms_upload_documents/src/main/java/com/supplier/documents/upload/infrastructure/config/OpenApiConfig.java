package com.supplier.documents.upload.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Upload Service API")
                        .version("1.0.0")
                        .description("Microservice for uploading documents and sending to external SOAP service")
                        .contact(new Contact()
                                .name("Supplier Documents Team")
                                .email("supplier-docs@company.com"))
                        .license(new License()
                                .name("Internal Use")
                                .url("https://company.com/licenses")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local server"),
                        new Server().url("http://localhost:" + serverPort + "/api/v1").description("API v1 server")
                ));
    }
}