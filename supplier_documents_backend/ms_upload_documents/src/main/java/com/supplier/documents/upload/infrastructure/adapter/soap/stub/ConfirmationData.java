package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Confirmation Data.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ConfirmationData", propOrder = {
    "externalDocumentId",
    "confirmationCode",
    "confirmationMessage",
    "processingStatus"
})
public class ConfirmationData {

    @XmlElement(name = "ExternalDocumentId", required = true)
    private String externalDocumentId;

    @XmlElement(name = "ConfirmationCode", required = true)
    private String confirmationCode;

    @XmlElement(name = "ConfirmationMessage")
    private String confirmationMessage;

    @XmlElement(name = "ProcessingStatus", required = true)
    private String processingStatus;

    public String getExternalDocumentId() { return externalDocumentId; }
    public void setExternalDocumentId(String externalDocumentId) { this.externalDocumentId = externalDocumentId; }

    public String getConfirmationCode() { return confirmationCode; }
    public void setConfirmationCode(String confirmationCode) { this.confirmationCode = confirmationCode; }

    public String getConfirmationMessage() { return confirmationMessage; }
    public void setConfirmationMessage(String confirmationMessage) { this.confirmationMessage = confirmationMessage; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
}
