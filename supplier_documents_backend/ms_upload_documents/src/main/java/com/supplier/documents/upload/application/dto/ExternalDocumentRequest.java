package com.supplier.documents.upload.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for external document request.
 * Used when sending document to external SOAP service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalDocumentRequest {

    private String documentId;
    private String fileName;
    private String documentTypeCode;
    private long fileSize;
    private String contentType;
    private String base64Content;
    private String checksum;
    private String uploadTimestamp;
    private String uploadedBy;
    private Map<String, String> metadata;
}