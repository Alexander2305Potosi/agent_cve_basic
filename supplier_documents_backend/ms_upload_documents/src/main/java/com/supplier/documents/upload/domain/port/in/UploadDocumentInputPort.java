package com.supplier.documents.upload.domain.port.in;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.DocumentUploadResponse;
import reactor.core.publisher.Mono;

/**
 * Input Port - Puerto de entrada para el caso de uso de subir documentos.
 * Define el contrato que los adaptadores de entrada (como REST) deben usar
 * para interactuar con la aplicación.
 * Esta interfaz es implementada por el Handler (Application Layer).
 */
public interface UploadDocumentInputPort {

    /**
     * Ejecuta el caso de uso de subir documento.
     * Delega al caso de uso concreto la lógica de negocio.
     *
     * @param request Datos del documento a subir
     * @return Mono con la respuesta del proceso
     */
    Mono<DocumentUploadResponse> uploadDocument(DocumentUploadRequest request);
}
