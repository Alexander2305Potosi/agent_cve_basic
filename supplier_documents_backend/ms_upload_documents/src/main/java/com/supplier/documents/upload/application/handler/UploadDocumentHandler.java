package com.supplier.documents.upload.application.handler;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.DocumentUploadResponse;
import com.supplier.documents.upload.domain.port.in.UploadDocumentInputPort;
import com.supplier.documents.upload.domain.usecase.UploadDocumentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler que implementa el Input Port.
 * Actúa como el adaptador de aplicación que recibe llamadas del adaptador de entrada (REST)
 * y delega al caso de uso concreto.
 *
 * Este handler implementa el patrón de "Application Service" que:
 * 1. Recibe el request del adaptador de entrada
 * 2. Delega al caso de uso de dominio
 * 3. Retorna la respuesta al adaptador
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadDocumentHandler implements UploadDocumentInputPort {

    private final UploadDocumentUseCase uploadDocumentUseCase;

    /**
     * Implementación del puerto de entrada.
     * Delega la ejecución al caso de uso de dominio.
     *
     * @param request Datos del documento a subir
     * @return Mono con la respuesta del proceso
     */
    @Override
    public Mono<DocumentUploadResponse> uploadDocument(DocumentUploadRequest request) {
        log.info("[Handler] Delegating document upload to use case for file: {}", request.getFileName());

        return uploadDocumentUseCase.execute(request)
                .doOnSuccess(response -> log.info("[Handler] Document upload completed: documentId={}, status={}",
                        response.getDocumentId(), response.getStatus()))
                .doOnError(error -> log.error("[Handler] Document upload failed: {}", error.getMessage()));
    }
}
