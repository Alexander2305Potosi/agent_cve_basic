package com.supplier.documents.upload.domain.gateway;

import com.supplier.documents.upload.domain.entity.Document;
import reactor.core.publisher.Mono;

/**
 * Gateway para almacenamiento de documentos.
 * Puerto de salida hacia infraestructura de persistencia.
 */
public interface DocumentStorageGateway {

    /**
     * Almacena un documento.
     *
     * @param document Documento a almacenar
     * @return Mono con el documento almacenado
     */
    Mono<Document> store(Document document);

    /**
     * Recupera un documento por su ID.
     *
     * @param documentId ID del documento
     * @return Mono con el documento encontrado o empty
     */
    Mono<Document> retrieve(String documentId);

    /**
     * Elimina un documento por su ID.
     *
     * @param documentId ID del documento
     * @return Mono con true si se eliminó, false si no existía
     */
    Mono<Boolean> delete(String documentId);
}
