package com.supplier.documents.upload.domain.gateway;

import com.supplier.documents.upload.application.dto.ExternalDocumentRequest;
import com.supplier.documents.upload.application.dto.ExternalDocumentResponse;
import reactor.core.publisher.Mono;

/**
 * Gateway para envío de documentos a servicio externo.
 * Puerto de salida hacia servicio SOAP externo.
 */
public interface ExternalDocumentGateway {

    /**
     * Envía el documento al servicio externo.
     *
     * @param request Datos del documento a enviar
     * @return Mono con la respuesta del servicio externo
     */
    Mono<ExternalDocumentResponse> send(ExternalDocumentRequest request);

    /**
     * Verifica la conectividad con el servicio externo.
     *
     * @return Mono con true si está disponible
     */
    Mono<Boolean> healthCheck();
}
