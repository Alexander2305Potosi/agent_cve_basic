package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Document Data.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DocumentData", propOrder = {
    "documentId",
    "fileName",
    "documentType",
    "fileSize",
    "contentType",
    "content",
    "checksum",
    "uploadTimestamp",
    "uploadedBy"
})
public class DocumentData {

    @XmlElement(name = "DocumentId", required = true)
    private String documentId;

    @XmlElement(name = "FileName", required = true)
    private String fileName;

    @XmlElement(name = "DocumentType", required = true)
    private String documentType;

    @XmlElement(name = "FileSize")
    private long fileSize;

    @XmlElement(name = "ContentType", required = true)
    private String contentType;

    @XmlElement(name = "Content", required = true)
    private String content;

    @XmlElement(name = "Checksum", required = true)
    private String checksum;

    @XmlElement(name = "UploadTimestamp", required = true)
    private String uploadTimestamp;

    @XmlElement(name = "UploadedBy")
    private String uploadedBy;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public String getUploadTimestamp() { return uploadTimestamp; }
    public void setUploadTimestamp(String uploadTimestamp) { this.uploadTimestamp = uploadTimestamp; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
