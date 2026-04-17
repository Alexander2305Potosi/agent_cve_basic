package com.supplier.documents.upload.domain.usecase;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.ExternalDocumentRequest;
import com.supplier.documents.upload.application.dto.ExternalDocumentResponse;
import com.supplier.documents.upload.application.mapper.DocumentMapper;
import com.supplier.documents.upload.domain.entity.Document;
import com.supplier.documents.upload.domain.exception.DocumentValidationException;
import com.supplier.documents.upload.domain.gateway.DocumentRepositoryGateway;
import com.supplier.documents.upload.domain.gateway.DocumentStorageGateway;
import com.supplier.documents.upload.domain.gateway.ExternalDocumentGateway;
import com.supplier.documents.upload.domain.valueobject.DocumentId;
import com.supplier.documents.upload.domain.valueobject.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for UploadDocumentUseCase.
 * Verifies the complete business logic flow including validation,
 * storage, external service call, and persistence.
 */
@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

    @Mock
    private DocumentStorageGateway storageGateway;

    @Mock
    private ExternalDocumentGateway externalGateway;

    @Mock
    private DocumentRepositoryGateway repositoryGateway;

    @Mock
    private DocumentMapper documentMapper;

    private UploadDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UploadDocumentUseCase(
                storageGateway,
                externalGateway,
                repositoryGateway,
                documentMapper
        );
    }

    @Test
    void shouldExecuteSuccessfully() {
        // Given - Create valid request
        String base64Content = Base64.getEncoder().encodeToString("test content".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(base64Content)
                .uploadedBy("user@example.com")
                .metadata(new HashMap<>())
                .build();

        Document document = Document.create(
                DocumentId.generate(),
                "test.pdf",
                DocumentType.INVOICE,
                100,
                "application/pdf",
                "test content".getBytes(),
                "checksum123",
                "user@example.com",
                new HashMap<>()
        );

        ExternalDocumentResponse externalResponse = ExternalDocumentResponse.builder()
                .success(true)
                .externalDocumentId("EXT-123")
                .confirmationMessage("Document received")
                .processingStatus("PENDING")
                .build();

        // Mock behavior
        when(documentMapper.toEntity(any())).thenReturn(document);
        when(storageGateway.store(any())).thenReturn(Mono.just(document));
        when(documentMapper.toExternalRequest(any())).thenReturn(
                ExternalDocumentRequest.builder().documentId(document.getDocumentId().getValue()).build()
        );
        when(externalGateway.send(any())).thenReturn(Mono.just(externalResponse));
        when(repositoryGateway.save(any())).thenReturn(Mono.just(document));

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectNextMatches(response -> {
                    assert response != null;
                    return "SUCCESS".equals(response.getStatus()) &&
                            "test.pdf".equals(response.getFileName());
                })
                .verifyComplete();

        // Verify interactions
        verify(storageGateway).store(any(Document.class));
        verify(externalGateway).send(any(ExternalDocumentRequest.class));
        verify(repositoryGateway).save(any(Document.class));
    }

    @Test
    void shouldHandleExternalServiceFailure() {
        // Given
        String base64Content = Base64.getEncoder().encodeToString("test content".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(base64Content)
                .uploadedBy("user@example.com")
                .build();

        Document document = Document.create(
                DocumentId.generate(),
                "test.pdf",
                DocumentType.INVOICE,
                100,
                "application/pdf",
                "test content".getBytes(),
                "checksum123",
                "user@example.com",
                new HashMap<>()
        );

        when(documentMapper.toEntity(any())).thenReturn(document);
        when(storageGateway.store(any())).thenReturn(Mono.just(document));
        when(documentMapper.toExternalRequest(any())).thenReturn(
                ExternalDocumentRequest.builder().documentId(document.getDocumentId().getValue()).build()
        );
        when(externalGateway.send(any())).thenReturn(Mono.error(new RuntimeException("Connection timeout")));
        when(repositoryGateway.save(any())).thenReturn(Mono.just(document));

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectNextMatches(response -> "PARTIAL_SUCCESS".equals(response.getStatus()))
                .verifyComplete();
    }

    @Test
    void shouldRejectEmptyFileName() {
        // Given
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(Base64.getEncoder().encodeToString("test".getBytes()))
                .build();

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectError(DocumentValidationException.class)
                .verify();
    }

    @Test
    void shouldRejectFileExceedingSize() {
        // Given - Create content larger than 10MB
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        String base64Content = Base64.getEncoder().encodeToString(largeContent);

        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("large.pdf")
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(base64Content)
                .build();

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectError(DocumentValidationException.class)
                .verify();
    }

    @Test
    void shouldRejectInvalidContentType() {
        // Given
        String base64Content = Base64.getEncoder().encodeToString("test".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.exe")
                .documentTypeCode("INV")
                .contentType("application/x-executable")
                .base64Content(base64Content)
                .build();

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectError(DocumentValidationException.class)
                .verify();
    }

    @Test
    void shouldRejectNullFileName() {
        // Given
        String base64Content = Base64.getEncoder().encodeToString("test".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName(null)
                .documentTypeCode("INV")
                .contentType("application/pdf")
                .base64Content(base64Content)
                .build();

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectError(DocumentValidationException.class)
                .verify();
    }

    @Test
    void shouldRejectNullContentType() {
        // Given
        String base64Content = Base64.getEncoder().encodeToString("test".getBytes());
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .fileName("test.pdf")
                .documentTypeCode("INV")
                .contentType(null)
                .base64Content(base64Content)
                .build();

        // When & Then
        StepVerifier.create(useCase.execute(request))
                .expectError(DocumentValidationException.class)
                .verify();
    }
}
