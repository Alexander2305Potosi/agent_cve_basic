package com.supplier.documents.upload.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIdTest {

    @Test
    void shouldGenerateValidDocumentId() {
        DocumentId id = DocumentId.generate();

        assertNotNull(id);
        assertNotNull(id.getValue());
        assertTrue(id.isValid());
    }

    @Test
    void shouldCreateFromValidString() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        DocumentId id = DocumentId.from(uuid);

        assertNotNull(id);
        assertEquals(uuid, id.getValue());
        assertTrue(id.isValid());
    }

    @Test
    void shouldThrowExceptionForEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentId.from("");
        });
    }

    @Test
    void shouldThrowExceptionForNullString() {
        assertThrows(IllegalArgumentException.class, () -> {
            DocumentId.from(null);
        });
    }

    @Test
    void shouldNotValidateInvalidUUID() {
        DocumentId id = DocumentId.from("not-a-valid-uuid");

        assertFalse(id.isValid());
    }

    @Test
    void shouldBeEqualForSameValue() {
        DocumentId id1 = DocumentId.from("550e8400-e29b-41d4-a716-446655440000");
        DocumentId id2 = DocumentId.from("550e8400-e29b-41d4-a716-446655440000");

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValue() {
        DocumentId id1 = DocumentId.generate();
        DocumentId id2 = DocumentId.generate();

        assertNotEquals(id1, id2);
    }
}