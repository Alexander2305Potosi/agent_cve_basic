package com.supplier.documents.upload.domain.usecase;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.DocumentUploadResponse;
import com.supplier.documents.upload.application.dto.ExternalDocumentRequest;
import com.supplier.documents.upload.application.dto.ExternalDocumentResponse;
import com.supplier.documents.upload.application.mapper.DocumentMapper;
import com.supplier.documents.upload.domain.entity.Document;
import com.supplier.documents.upload.domain.exception.DocumentValidationException;
import com.supplier.documents.upload.domain.gateway.DocumentRepositoryGateway;
import com.supplier.documents.upload.domain.gateway.DocumentStorageGateway;
import com.supplier.documents.upload.domain.gateway.ExternalDocumentGateway;
import com.supplier.documents.upload.domain.valueobject.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Caso de uso concreto para subir documentos.
 * Contiene la lógica de negocio orquestando los gateways necesarios.
 * Este es un servicio de dominio que implementa el flujo completo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadDocumentUseCase {

    private final DocumentStorageGateway storageGateway;
    private final ExternalDocumentGateway externalGateway;
    private final DocumentRepositoryGateway repositoryGateway;
    private final DocumentMapper documentMapper;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Ejecuta el flujo completo de subida de documentos:
     * 1. Validación
     * 2. Almacenamiento local
     * 3. Envío a servicio externo
     * 4. Persistencia
     *
     * @param request Datos del documento a subir
     * @return Mono con la respuesta del proceso
     */
    public Mono<DocumentUploadResponse> execute(DocumentUploadRequest request) {
        log.info("[UseCase] Starting document upload process for file: {}", request.getFileName());

        return validateRequest(request)
                .flatMap(this::createAndStoreDocument)
                .flatMap(this::sendToExternalService)
                .flatMap(this::persistAndBuildResponse)
                .doOnSuccess(response -> log.info("[UseCase] Document upload completed: {}",
                        response.getDocumentId()))
                .doOnError(error -> log.error("[UseCase] Document upload failed: {}", error.getMessage()));
    }

    private Mono<DocumentUploadRequest> validateRequest(DocumentUploadRequest request) {
        return Mono.fromCallable(() -> {
            log.debug("[UseCase] Validating request for file: {}", request.getFileName());

            validateFileName(request.getFileName());
            validateContent(request.getBase64Content());
            validateFileSize(request.getBase64Content());
            validateContentType(request.getContentType());
            validateDocumentType(request.getDocumentTypeCode());

            return request;
        });
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new DocumentValidationException("File name is required");
        }
        if (fileName.length() > 255) {
            throw new DocumentValidationException("File name must not exceed 255 characters");
        }
    }

    private void validateContent(String base64Content) {
        if (base64Content == null || base64Content.isEmpty()) {
            throw new DocumentValidationException("Document content is required");
        }
    }

    private void validateFileSize(String base64Content) {
        long approximateSize = (base64Content.length() * 3) / 4;
        if (approximateSize > MAX_FILE_SIZE) {
            throw new DocumentValidationException(
                    String.format("File size exceeds maximum allowed (%d MB)", MAX_FILE_SIZE / (1024 * 1024)));
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null) {
            throw new DocumentValidationException("Content type is required");
        }
        boolean allowed = contentType.equals("application/pdf") ||
                contentType.equals("image/jpeg") ||
                contentType.equals("image/png") ||
                contentType.equals("application/xml") ||
                contentType.equals("text/xml");
        if (!allowed) {
            throw new DocumentValidationException(
                    "Invalid content type. Allowed types: PDF, JPEG, PNG, XML");
        }
    }

    private void validateDocumentType(String documentTypeCode) {
        if (documentTypeCode == null) {
            throw new DocumentValidationException("Document type code is required");
        }
        DocumentType type = DocumentType.fromCode(documentTypeCode);
        if (type == DocumentType.OTHER && !documentTypeCode.equalsIgnoreCase("OTH")) {
            throw new DocumentValidationException(
                    "Invalid document type code. Allowed: INV, PO, CON, CERT, PROP, TECH, OTH");
        }
    }

    private Mono<Document> createAndStoreDocument(DocumentUploadRequest request) {
        return Mono.fromCallable(() -> documentMapper.toEntity(request))
                .flatMap(document -> {
                    log.debug("[UseCase] Storing document: {}", document.getDocumentId());
                    return storageGateway.store(document);
                });
    }

    private Mono<Tuple<Document, ExternalDocumentResponse>> sendToExternalService(Document document) {
        ExternalDocumentRequest externalRequest = documentMapper.toExternalRequest(document);

        return externalGateway.send(externalRequest)
                .map(externalResponse -> new Tuple<>(document, externalResponse))
                .onErrorResume(error -> {
                    log.warn("[UseCase] External service call failed: {}", error.getMessage());
                    ExternalDocumentResponse errorResponse = ExternalDocumentResponse.builder()
                            .success(false)
                            .errorMessage(error.getMessage())
                            .build();
                    return Mono.just(new Tuple<>(document, errorResponse));
                });
    }

    private Mono<DocumentUploadResponse> persistAndBuildResponse(Tuple<Document, ExternalDocumentResponse> tuple) {
        Document document = tuple.getFirst();
        ExternalDocumentResponse externalResponse = tuple.getSecond();

        return repositoryGateway.save(document)
                .map(savedDocument -> buildResponse(savedDocument, externalResponse));
    }

    private DocumentUploadResponse buildResponse(Document document, ExternalDocumentResponse externalResponse) {
        boolean success = externalResponse.isSuccess();
        String status = success ? "SUCCESS" : "PARTIAL_SUCCESS";
        String errorMessage = success ? null : externalResponse.getErrorMessage();

        return DocumentUploadResponse.builder()
                .documentId(document.getDocumentId().getValue())
                .status(status)
                .fileName(document.getFileName())
                .externalDocumentId(success ? externalResponse.getExternalDocumentId() : null)
                .externalConfirmationMessage(success ? externalResponse.getConfirmationMessage() : null)
                .externalProcessingStatus(success ? externalResponse.getProcessingStatus() : null)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    /**
     * Clase auxiliar para mantener el documento y la respuesta externa juntos.
     */
    private static class Tuple<T1, T2> {
        private final T1 first;
        private final T2 second;

        Tuple(T1 first, T2 second) {
            this.first = first;
            this.second = second;
        }

        T1 getFirst() {
            return first;
        }

        T2 getSecond() {
            return second;
        }
    }
}
