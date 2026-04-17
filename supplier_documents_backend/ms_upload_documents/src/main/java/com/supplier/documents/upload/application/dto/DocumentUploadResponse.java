package com.supplier.documents.upload.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for document upload")
public class DocumentUploadResponse {

    @Schema(description = "Unique identifier of the uploaded document", example = "550e8400-e29b-41d4-a716-446655440000")
    private String documentId;

    @Schema(description = "Status of the upload operation", example = "SUCCESS")
    private String status;

    @Schema(description = "Name of the uploaded file", example = "invoice_001.pdf")
    private String fileName;

    @Schema(description = "ID assigned by external SOAP service", example = "EXT-12345678")
    private String externalDocumentId;

    @Schema(description = "Confirmation message from external service", example = "Document received and validated")
    private String externalConfirmationMessage;

    @Schema(description = "Processing status in external system", example = "PENDING_REVIEW")
    private String externalProcessingStatus;

    @Schema(description = "Error message if operation failed")
    private String errorMessage;

    @Schema(description = "Timestamp of the response", example = "2024-04-16T14:30:00Z")
    private String timestamp;

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}