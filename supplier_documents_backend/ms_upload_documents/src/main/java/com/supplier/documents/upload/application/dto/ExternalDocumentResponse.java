package com.supplier.documents.upload.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for external document response.
 * Received from external SOAP service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalDocumentResponse {

    private boolean success;
    private String externalDocumentId;
    private String confirmationCode;
    private String confirmationMessage;
    private String processingStatus;
    private String receivedTimestamp;
    private String errorCode;
    private String errorMessage;
}