package com.supplier.documents.upload.infrastructure.rest;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.DocumentUploadResponse;
import com.supplier.documents.upload.domain.port.in.UploadDocumentInputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for DocumentRouteRest controller.
 * Verifies REST endpoint integration with use case.
 */
@ExtendWith(MockitoExtension.class)
class DocumentRouteRestTest {

    @Mock
    private UploadDocumentInputPort uploadDocumentInputPort;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        DocumentRouteRest controller = new DocumentRouteRest(uploadDocumentInputPort);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void shouldUploadDocumentSuccessfully() {
        // Given
        String base64Content = Base64.getEncoder().encodeToString("test content".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(base64Content)
                .uploadedBy("user@example.com")
                .metadata(new HashMap<>())
                .build();

        DocumentUploadResponse response = DocumentUploadResponse.builder()
                .documentId("DOC-123")
                .fileName("test.pdf")
                .status("SUCCESS")
                .externalDocumentId("EXT-456")
                .timestamp("2024-01-01T12:00:00")
                .build();

        when(uploadDocumentInputPort.uploadDocument(any())).thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
                .uri("/api/v1/documents/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DocumentUploadResponse.class)
                .value(res -> {
                    assert res.getDocumentId().equals("DOC-123");
                    assert res.getStatus().equals("SUCCESS");
                    assert res.getExternalDocumentId().equals("EXT-456");
                });
    }

    @Test
    void shouldReturnErrorWhenUseCaseFails() {
        // Given
        String base64Content = Base64.getEncoder().encodeToString("test content".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(base64Content)
                .uploadedBy("user@example.com")
                .metadata(new HashMap<>())
                .build();

        when(uploadDocumentInputPort.uploadDocument(any()))
                .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        // When & Then
        webTestClient.post()
                .uri("/api/v1/documents/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void healthCheckShouldReturnOk() {
        // When & Then
        webTestClient.get()
                .uri("/api/v1/documents/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("ms-upload-documents");
    }

    @Test
    void shouldRejectInvalidRequest() {
        // Given - Invalid request without fileName
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(Base64.getEncoder().encodeToString("test".getBytes()))
                .build();

        // When & Then
        webTestClient.post()
                .uri("/api/v1/documents/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
