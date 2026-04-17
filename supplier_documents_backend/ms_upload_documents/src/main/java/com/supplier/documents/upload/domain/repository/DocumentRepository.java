package com.supplier.documents.upload.domain.repository;

import com.supplier.documents.upload.domain.entity.Document;
import com.supplier.documents.upload.domain.valueobject.DocumentId;
import reactor.core.publisher.Mono;

public interface DocumentRepository {

    Mono<Document> save(Document document);

    Mono<Document> findById(DocumentId documentId);

    Mono<Boolean> existsById(DocumentId documentId);

    Mono<Void> deleteById(DocumentId documentId);
}