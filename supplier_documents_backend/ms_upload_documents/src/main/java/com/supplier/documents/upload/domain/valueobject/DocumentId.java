package com.supplier.documents.upload.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class DocumentId {

    private final String value;

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID().toString());
    }

    public static DocumentId from(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Document ID cannot be empty");
        }
        return new DocumentId(value);
    }

    public String getValue() {
        return value;
    }

    public boolean isValid() {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}