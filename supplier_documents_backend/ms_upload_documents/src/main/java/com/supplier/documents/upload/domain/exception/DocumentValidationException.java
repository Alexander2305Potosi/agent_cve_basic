package com.supplier.documents.upload.domain.exception;

public class DocumentValidationException extends DomainException {

    private static final String ERROR_CODE = "DOC_VALIDATION_ERROR";

    public DocumentValidationException(String message) {
        super(message, ERROR_CODE);
    }

    public DocumentValidationException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
}