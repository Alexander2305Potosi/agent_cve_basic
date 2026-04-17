package com.supplier.documents.upload.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTypeTest {

    @Test
    void shouldReturnCorrectCodeAndDescription() {
        assertEquals("INV", DocumentType.INVOICE.getCode());
        assertEquals("Invoice", DocumentType.INVOICE.getDescription());
        assertEquals("application/pdf", DocumentType.INVOICE.getDefaultContentType());

        assertEquals("PO", DocumentType.PURCHASE_ORDER.getCode());
        assertEquals("Purchase Order", DocumentType.PURCHASE_ORDER.getDescription());
    }

    @Test
    void shouldFindByCode() {
        assertEquals(DocumentType.INVOICE, DocumentType.fromCode("INV"));
        assertEquals(DocumentType.PURCHASE_ORDER, DocumentType.fromCode("PO"));
        assertEquals(DocumentType.CONTRACT, DocumentType.fromCode("CON"));
        assertEquals(DocumentType.CERTIFICATE, DocumentType.fromCode("CERT"));
        assertEquals(DocumentType.PROPOSAL, DocumentType.fromCode("PROP"));
        assertEquals(DocumentType.TECHNICAL_SPEC, DocumentType.fromCode("TECH"));
        assertEquals(DocumentType.OTHER, DocumentType.fromCode("OTH"));
    }

    @Test
    void shouldReturnOtherForUnknownCode() {
        assertEquals(DocumentType.OTHER, DocumentType.fromCode("UNKNOWN"));
        assertEquals(DocumentType.OTHER, DocumentType.fromCode(null));
    }

    @Test
    void shouldFindByFilename() {
        assertEquals(DocumentType.INVOICE, DocumentType.fromFilename("invoice_001.pdf"));
        assertEquals(DocumentType.INVOICE, DocumentType.fromFilename("INVOICE_march.pdf"));
        assertEquals(DocumentType.PURCHASE_ORDER, DocumentType.fromFilename("purchase_order.pdf"));
        assertEquals(DocumentType.PURCHASE_ORDER, DocumentType.fromFilename("order_confirmation.pdf"));
        assertEquals(DocumentType.CONTRACT, DocumentType.fromFilename("contract_v1.pdf"));
        assertEquals(DocumentType.CERTIFICATE, DocumentType.fromFilename("certificate.pdf"));
        assertEquals(DocumentType.PROPOSAL, DocumentType.fromFilename("proposal_2024.pdf"));
        assertEquals(DocumentType.TECHNICAL_SPEC, DocumentType.fromFilename("technical_spec.pdf"));
        assertEquals(DocumentType.TECHNICAL_SPEC, DocumentType.fromFilename("spec_document.pdf"));
    }

    @Test
    void shouldReturnOtherForUnknownFilename() {
        assertEquals(DocumentType.OTHER, DocumentType.fromFilename("random_file.pdf"));
        assertEquals(DocumentType.OTHER, DocumentType.fromFilename(null));
    }

    @Test
    void shouldBeCaseInsensitive() {
        assertEquals(DocumentType.INVOICE, DocumentType.fromCode("inv"));
        assertEquals(DocumentType.INVOICE, DocumentType.fromCode("INV"));
        assertEquals(DocumentType.INVOICE, DocumentType.fromCode("Inv"));
    }
}