package com.supplier.documents.upload.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for document upload")
public class DocumentUploadRequest {

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must not exceed 255 characters")
    @Schema(description = "Name of the file", example = "invoice_001.pdf")
    private String fileName;

    @NotBlank(message = "Document type is required")
    @Pattern(regexp = "^(INV|PO|CON|CERT|PROP|TECH|OTH)$", message = "Invalid document type code")
    @Schema(description = "Document type code (INV, PO, CON, CERT, PROP, TECH, OTH)", example = "INV")
    private String documentTypeCode;

    @NotBlank(message = "Content type is required")
    @Schema(description = "MIME type of the document", example = "application/pdf")
    private String contentType;

    @NotNull(message = "Content is required")
    @Schema(description = "Base64 encoded document content")
    private String base64Content;

    @Size(max = 100, message = "Uploaded by must not exceed 100 characters")
    @Schema(description = "User who uploaded the document", example = "john.doe@company.com")
    private String uploadedBy;

    @Schema(description = "Additional metadata key-value pairs")
    private Map<String, String> metadata;
}
