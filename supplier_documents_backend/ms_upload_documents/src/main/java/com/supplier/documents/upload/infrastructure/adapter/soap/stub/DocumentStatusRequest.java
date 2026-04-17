package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Document Status Request.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "header",
    "externalDocumentId"
})
@XmlRootElement(name = "DocumentStatusRequest", namespace = "http://supplier.com/document-service")
public class DocumentStatusRequest {

    @XmlElement(name = "Header", required = true)
    private RequestHeader header;

    @XmlElement(name = "ExternalDocumentId", required = true)
    private String externalDocumentId;

    public RequestHeader getHeader() {
        return header;
    }

    public void setHeader(RequestHeader header) {
        this.header = header;
    }

    public String getExternalDocumentId() {
        return externalDocumentId;
    }

    public void setExternalDocumentId(String externalDocumentId) {
        this.externalDocumentId = externalDocumentId;
    }
}
