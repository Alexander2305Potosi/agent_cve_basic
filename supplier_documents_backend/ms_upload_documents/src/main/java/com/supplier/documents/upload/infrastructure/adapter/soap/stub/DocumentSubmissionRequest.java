package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Document Submission Request.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "header",
    "documentData",
    "metadata"
})
@XmlRootElement(name = "DocumentSubmissionRequest", namespace = "http://supplier.com/document-service")
public class DocumentSubmissionRequest {

    @XmlElement(name = "Header", required = true)
    private RequestHeader header;

    @XmlElement(name = "DocumentData", required = true)
    private DocumentData documentData;

    @XmlElement(name = "Metadata")
    private MetadataList metadata;

    public RequestHeader getHeader() {
        return header;
    }

    public void setHeader(RequestHeader header) {
        this.header = header;
    }

    public DocumentData getDocumentData() {
        return documentData;
    }

    public void setDocumentData(DocumentData documentData) {
        this.documentData = documentData;
    }

    public MetadataList getMetadata() {
        return metadata;
    }

    public void setMetadata(MetadataList metadata) {
        this.metadata = metadata;
    }
}
