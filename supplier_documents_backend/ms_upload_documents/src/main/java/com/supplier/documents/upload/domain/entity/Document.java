package com.supplier.documents.upload.domain.entity;

import com.supplier.documents.upload.domain.valueobject.DocumentId;
import com.supplier.documents.upload.domain.valueobject.DocumentType;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@EqualsAndHashCode(of = "documentId")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Document {

    private final DocumentId documentId;
    private final String fileName;
    private final DocumentType documentType;
    private final long fileSize;
    private final String contentType;
    private final byte[] content;
    private final String checksum;
    private final LocalDateTime uploadTimestamp;
    private final String uploadedBy;
    private final Map<String, String> metadata;

    public static Document create(
            DocumentId documentId,
            String fileName,
            DocumentType documentType,
            long fileSize,
            String contentType,
            byte[] content,
            String checksum,
            String uploadedBy,
            Map<String, String> metadata) {

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Document content cannot be empty");
        }
        if (checksum == null || checksum.trim().isEmpty()) {
            throw new IllegalArgumentException("Checksum cannot be empty");
        }

        return Document.builder()
                .documentId(documentId)
                .fileName(fileName)
                .documentType(documentType)
                .fileSize(fileSize)
                .contentType(contentType)
                .content(content)
                .checksum(checksum)
                .uploadTimestamp(LocalDateTime.now())
                .uploadedBy(uploadedBy)
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .build();
    }

    public String getDocumentTypeCode() {
        return documentType != null ? documentType.getCode() : null;
    }

    public String getDocumentTypeDescription() {
        return documentType != null ? documentType.getDescription() : null;
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }

    public void addMetadata(String key, String value) {
        if (key != null && !key.trim().isEmpty() && value != null) {
            this.metadata.put(key, value);
        }
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    public boolean isPdf() {
        return "application/pdf".equalsIgnoreCase(contentType);
    }

    public boolean exceedsSize(long maxSizeInBytes) {
        return fileSize > maxSizeInBytes;
    }
}
