package com.supplier.documents.upload.application.mapper;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.ExternalDocumentRequest;
import com.supplier.documents.upload.domain.entity.Document;
import com.supplier.documents.upload.domain.valueobject.DocumentId;
import com.supplier.documents.upload.domain.valueobject.DocumentType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;

/**
 * Mapper for converting between DTOs and Domain entities.
 * Manually implemented to avoid MapStruct dependencies.
 */
@Component
public class DocumentMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Converts a DocumentUploadRequest to a Document entity.
     */
    public Document toEntity(DocumentUploadRequest request) {
        if (request == null) {
            return null;
        }

        byte[] content = decodeBase64(request.getBase64Content());

        return Document.create(
                DocumentId.generate(),
                request.getFileName(),
                DocumentType.fromCode(request.getDocumentTypeCode()),
                calculateSize(request.getBase64Content()),
                request.getContentType(),
                content,
                calculateChecksum(request.getBase64Content()),
                request.getUploadedBy(),
                request.getMetadata() != null ? new HashMap<>(request.getMetadata()) : new HashMap<>()
        );
    }

    /**
     * Converts a Document entity to an ExternalDocumentRequest.
     */
    public ExternalDocumentRequest toExternalRequest(Document document) {
        if (document == null) {
            return null;
        }

        return ExternalDocumentRequest.builder()
                .documentId(document.getDocumentId().getValue())
                .fileName(document.getFileName())
                .documentTypeCode(document.getDocumentTypeCode())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .base64Content(encodeBase64(document.getContent()))
                .checksum(document.getChecksum())
                .uploadTimestamp(document.getUploadTimestamp().format(FORMATTER))
                .uploadedBy(document.getUploadedBy())
                .metadata(document.getMetadata())
                .build();
    }

    private byte[] decodeBase64(String base64Content) {
        if (base64Content == null) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(base64Content);
    }

    private String encodeBase64(byte[] content) {
        if (content == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(content);
    }

    private long calculateSize(String base64Content) {
        if (base64Content == null) {
            return 0;
        }
        // Approximate size: base64 is ~4/3 of original
        return (base64Content.length() * 3) / 4;
    }

    private String calculateChecksum(String base64Content) {
        if (base64Content == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base64Content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}