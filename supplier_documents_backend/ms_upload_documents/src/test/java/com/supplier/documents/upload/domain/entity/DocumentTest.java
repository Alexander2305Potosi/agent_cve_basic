package com.supplier.documents.upload.domain.entity;

import com.supplier.documents.upload.domain.valueobject.DocumentId;
import com.supplier.documents.upload.domain.valueobject.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {

    @Test
    void shouldCreateDocumentSuccessfully() {
        byte[] content = "test content".getBytes();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");

        Document document = Document.create(
                DocumentId.generate(),
                "test.pdf",
                DocumentType.INVOICE,
                100L,
                "application/pdf",
                content,
                "abc123",
                "user@example.com",
                metadata
        );

        assertNotNull(document);
        assertNotNull(document.getDocumentId());
        assertEquals("test.pdf", document.getFileName());
        assertEquals(DocumentType.INVOICE, document.getDocumentType());
        assertEquals(100L, document.getFileSize());
        assertEquals("application/pdf", document.getContentType());
        assertEquals("INV", document.getDocumentTypeCode());
        assertEquals("Invoice", document.getDocumentTypeDescription());
        assertEquals("user@example.com", document.getUploadedBy());
    }

    @Test
    void shouldThrowExceptionForEmptyFileName() {
        byte[] content = "test".getBytes();

        assertThrows(IllegalArgumentException.class, () -> {
            Document.create(
                    DocumentId.generate(),
                    "",
                    DocumentType.INVOICE,
                    100L,
                    "application/pdf",
                    content,
                    "abc123",
                    "user@example.com",
                    null
            );
        });
    }

    @Test
    void shouldThrowExceptionForEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> {
            Document.create(
                    DocumentId.generate(),
                    "test.pdf",
                    DocumentType.INVOICE,
                    100L,
                    "application/pdf",
                    new byte[0],
                    "abc123",
                    "user@example.com",
                    null
            );
        });
    }

    @Test
    void shouldThrowExceptionForEmptyChecksum() {
        byte[] content = "test".getBytes();

        assertThrows(IllegalArgumentException.class, () -> {
            Document.create(
                    DocumentId.generate(),
                    "test.pdf",
                    DocumentType.INVOICE,
                    100L,
                    "application/pdf",
                    content,
                    "",
                    "user@example.com",
                    null
            );
        });
    }

    @Test
    void shouldIdentifyImage() {
        byte[] content = "test".getBytes();

        Document imageDoc = Document.create(
                DocumentId.generate(),
                "image.jpg",
                DocumentType.CERTIFICATE,
                100L,
                "image/jpeg",
                content,
                "abc123",
                "user@example.com",
                null
        );

        assertTrue(imageDoc.isImage());
        assertFalse(imageDoc.isPdf());
    }

    @Test
    void shouldIdentifyPdf() {
        byte[] content = "test".getBytes();

        Document pdfDoc = Document.create(
                DocumentId.generate(),
                "doc.pdf",
                DocumentType.INVOICE,
                100L,
                "application/pdf",
                content,
                "abc123",
                "user@example.com",
                null
        );

        assertTrue(pdfDoc.isPdf());
        assertFalse(pdfDoc.isImage());
    }

    @Test
    void shouldCheckSizeExceeded() {
        byte[] content = "test content here".getBytes();

        Document document = Document.create(
                DocumentId.generate(),
                "test.pdf",
                DocumentType.INVOICE,
                100L,
                "application/pdf",
                content,
                "abc123",
                "user@example.com",
                null
        );

        assertTrue(document.exceedsSize(10));
        assertFalse(document.exceedsSize(200));
    }

    @Test
    void shouldAddMetadata() {
        byte[] content = "test".getBytes();
        Map<String, String> metadata = new HashMap<>();

        Document document = Document.create(
                DocumentId.generate(),
                "test.pdf",
                DocumentType.INVOICE,
                100L,
                "application/pdf",
                content,
                "abc123",
                "user@example.com",
                metadata
        );

        document.addMetadata("key1", "value1");
        document.addMetadata("", "value2");  // Should be ignored
        document.addMetadata("key2", null);  // Should be ignored

        assertEquals("value1", document.getMetadata().get("key1"));
        assertFalse(document.getMetadata().containsKey(""));
        assertFalse(document.getMetadata().containsKey("key2"));
    }
}