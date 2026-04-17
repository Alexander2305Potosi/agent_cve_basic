package com.supplier.documents.upload.application.handler;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.DocumentUploadResponse;
import com.supplier.documents.upload.domain.port.in.UploadDocumentInputPort;
import com.supplier.documents.upload.domain.usecase.UploadDocumentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for UploadDocumentHandler.
 * Verifies that the handler correctly implements UploadDocumentInputPort
 * and delegates to the UploadDocumentUseCase.
 */
@ExtendWith(MockitoExtension.class)
class UploadDocumentHandlerTest {

    @Mock
    private UploadDocumentUseCase uploadDocumentUseCase;

    private UploadDocumentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UploadDocumentHandler(uploadDocumentUseCase);
    }

    @Test
    void shouldDelegateToUseCase() {
        // Given - Create valid request
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content("dGVzdCBjb250ZW50") // base64 encoded "test content"
                .uploadedBy("user@example.com")
                .metadata(new HashMap<>())
                .build();

        DocumentUploadResponse expectedResponse = DocumentUploadResponse.builder()
                .documentId("DOC-123")
                .status("SUCCESS")
                .fileName("test.pdf")
                .externalDocumentId("EXT-456")
                .build();

        // Mock behavior - handler delegates to use case
        when(uploadDocumentUseCase.execute(any())).thenReturn(Mono.just(expectedResponse));

        // When & Then
        StepVerifier.create(handler.uploadDocument(request))
                .expectNextMatches(response -> {
                    assert response != null;
                    return "SUCCESS".equals(response.getStatus()) &&
                            "test.pdf".equals(response.getFileName()) &&
                            "EXT-456".equals(response.getExternalDocumentId());
                })
                .verifyComplete();

        // Verify that handler delegates to use case
        verify(uploadDocumentUseCase).execute(any(DocumentUploadRequest.class));
    }

    @Test
    void shouldPropagateErrorsFromUseCase() {
        // Given
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content("dGVzdCBjb250ZW50")
                .build();

        // Mock error from use case
        when(uploadDocumentUseCase.execute(any()))
                .thenReturn(Mono.error(new RuntimeException("Use case failed")));

        // When & Then
        StepVerifier.create(handler.uploadDocument(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldImplementInputPort() {
        // Verify that handler implements the interface
        assert handler instanceof UploadDocumentInputPort :
                "Handler should implement UploadDocumentInputPort";
    }
}
