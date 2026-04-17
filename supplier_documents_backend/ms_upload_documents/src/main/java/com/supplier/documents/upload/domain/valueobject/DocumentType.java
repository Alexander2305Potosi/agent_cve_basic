package com.supplier.documents.upload.domain.valueobject;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum DocumentType {
    INVOICE("INV", "Invoice", "application/pdf"),
    PURCHASE_ORDER("PO", "Purchase Order", "application/pdf"),
    CONTRACT("CON", "Contract", "application/pdf"),
    CERTIFICATE("CERT", "Certificate", "application/pdf"),
    PROPOSAL("PROP", "Proposal", "application/pdf"),
    TECHNICAL_SPEC("TECH", "Technical Specification", "application/pdf"),
    OTHER("OTH", "Other", "application/octet-stream");

    private final String code;
    private final String description;
    private final String defaultContentType;

    DocumentType(String code, String description, String defaultContentType) {
        this.code = code;
        this.description = description;
        this.defaultContentType = defaultContentType;
    }

    public static DocumentType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(OTHER);
    }

    public static DocumentType fromFilename(String filename) {
        if (filename == null) {
            return OTHER;
        }
        String lower = filename.toLowerCase();
        if (lower.contains("invoice")) return INVOICE;
        if (lower.contains("purchase") || lower.contains("order")) return PURCHASE_ORDER;
        if (lower.contains("contract")) return CONTRACT;
        if (lower.contains("certificate")) return CERTIFICATE;
        if (lower.contains("proposal")) return PROPOSAL;
        if (lower.contains("technical") || lower.contains("spec")) return TECHNICAL_SPEC;
        return OTHER;
    }
}