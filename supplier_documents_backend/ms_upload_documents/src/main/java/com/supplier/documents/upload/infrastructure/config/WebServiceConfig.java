package com.supplier.documents.upload.infrastructure.config;

import com.supplier.documents.upload.infrastructure.adapter.soap.stub.DocumentServicePortType;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SOAP Web Services.
 * Configures the client factory for external document service.
 */
@Configuration
public class WebServiceConfig {

    @Value("${external.soap.service.url:http://localhost:8081/ws/documents}")
    private String soapServiceUrl;

    @Bean
    public DocumentServicePortType documentServicePort() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(DocumentServicePortType.class);
        factory.setAddress(soapServiceUrl);

        // Configure timeouts
        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("javax.xml.ws.client.connectionTimeout", 30000);
        properties.put("javax.xml.ws.client.receiveTimeout", 60000);
        factory.setProperties(properties);

        return (DocumentServicePortType) factory.create();
    }
}