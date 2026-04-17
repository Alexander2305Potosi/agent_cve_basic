package com.supplier.documents.upload.domain.exception;

public class DocumentNotFoundException extends DomainException {

    private static final String ERROR_CODE = "DOC_NOT_FOUND";

    public DocumentNotFoundException(String documentId) {
        super(String.format("Document with ID '%s' not found", documentId), ERROR_CODE);
    }
}