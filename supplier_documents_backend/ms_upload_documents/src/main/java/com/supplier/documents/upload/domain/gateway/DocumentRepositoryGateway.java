package com.supplier.documents.upload.domain.gateway;

import com.supplier.documents.upload.domain.entity.Document;
import com.supplier.documents.upload.domain.valueobject.DocumentId;
import reactor.core.publisher.Mono;

/**
 * Gateway para repositorio de documentos.
 * Puerto de salida para operaciones CRUD de documentos.
 */
public interface DocumentRepositoryGateway {

    /**
     * Guarda un documento en el repositorio.
     *
     * @param document Documento a guardar
     * @return Mono con el documento guardado
     */
    Mono<Document> save(Document document);

    /**
     * Busca un documento por su ID.
     *
     * @param documentId ID del documento
     * @return Mono con el documento encontrado
     * @throws com.supplier.documents.upload.domain.exception.DocumentNotFoundException si no existe
     */
    Mono<Document> findById(DocumentId documentId);

    /**
     * Verifica si existe un documento con el ID dado.
     *
     * @param documentId ID del documento
     * @return Mono con true si existe, false si no
     */
    Mono<Boolean> existsById(DocumentId documentId);

    /**
     * Elimina un documento por su ID.
     *
     * @param documentId ID del documento a eliminar
     * @return Mono vacío
     */
    Mono<Void> deleteById(DocumentId documentId);
}
