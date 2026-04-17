package com.supplier.documents.upload.infrastructure.adapter.persistence;

import com.supplier.documents.upload.domain.gateway.DocumentRepositoryGateway;
import com.supplier.documents.upload.domain.gateway.DocumentStorageGateway;
import com.supplier.documents.upload.domain.entity.Document;
import com.supplier.documents.upload.domain.exception.DocumentNotFoundException;
import com.supplier.documents.upload.domain.valueobject.DocumentId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter - Adaptador de persistencia en memoria.
 * Implementa tanto DocumentStorageGateway como DocumentRepositoryGateway.
 */
@Component
@Slf4j
public class InMemoryDocumentAdapter implements DocumentStorageGateway, DocumentRepositoryGateway {

    private final ConcurrentHashMap<String, Document> storage = new ConcurrentHashMap<>();

    // === DocumentStorageGateway implementation ===

    @Override
    public Mono<Document> store(Document document) {
        return Mono.fromCallable(() -> {
            storage.put(document.getDocumentId().getValue(), document);
            log.info("[Adapter-Persistence] Document stored: {}", document.getDocumentId());
            return document;
        });
    }

    @Override
    public Mono<Document> retrieve(String documentId) {
        return Mono.fromCallable(() -> storage.get(documentId));
    }

    @Override
    public Mono<Boolean> delete(String documentId) {
        return Mono.fromCallable(() -> {
            Document removed = storage.remove(documentId);
            boolean deleted = removed != null;
            log.info("[Adapter-Persistence] Document deleted: {}, success={}",
                    documentId, deleted);
            return deleted;
        });
    }

    // === DocumentRepositoryGateway implementation ===

    @Override
    public Mono<Document> save(Document document) {
        return Mono.fromCallable(() -> {
            storage.put(document.getDocumentId().getValue(), document);
            log.info("[Adapter-Persistence] Document saved to repository: {}",
                    document.getDocumentId());
            return document;
        });
    }

    @Override
    public Mono<Document> findById(DocumentId documentId) {
        return Mono.fromCallable(() -> {
            Document document = storage.get(documentId.getValue());
            if (document == null) {
                throw new DocumentNotFoundException(documentId.getValue());
            }
            return document;
        });
    }

    @Override
    public Mono<Boolean> existsById(DocumentId documentId) {
        return Mono.fromCallable(() -> storage.containsKey(documentId.getValue()));
    }

    @Override
    public Mono<Void> deleteById(DocumentId documentId) {
        return Mono.fromCallable(() -> {
            storage.remove(documentId.getValue());
            log.info("[Adapter-Persistence] Document removed from repository: {}",
                    documentId);
            return (Void) null;
        });
    }

    /**
     * Método de utilidad para limpiar el almacenamiento (útil en tests).
     */
    public void clear() {
        storage.clear();
        log.info("[Adapter-Persistence] Storage cleared");
    }

    /**
     * Método de utilidad para obtener el tamaño actual.
     */
    public int size() {
        return storage.size();
    }
}