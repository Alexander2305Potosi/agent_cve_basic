package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Status Data.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StatusData", propOrder = {
    "externalDocumentId",
    "currentStatus",
    "statusDescription",
    "lastUpdateTimestamp"
})
public class StatusData {

    @XmlElement(name = "ExternalDocumentId", required = true)
    private String externalDocumentId;

    @XmlElement(name = "CurrentStatus", required = true)
    private String currentStatus;

    @XmlElement(name = "StatusDescription")
    private String statusDescription;

    @XmlElement(name = "LastUpdateTimestamp", required = true)
    private String lastUpdateTimestamp;

    public String getExternalDocumentId() { return externalDocumentId; }
    public void setExternalDocumentId(String externalDocumentId) { this.externalDocumentId = externalDocumentId; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }

    public String getStatusDescription() { return statusDescription; }
    public void setStatusDescription(String statusDescription) { this.statusDescription = statusDescription; }

    public String getLastUpdateTimestamp() { return lastUpdateTimestamp; }
    public void setLastUpdateTimestamp(String lastUpdateTimestamp) { this.lastUpdateTimestamp = lastUpdateTimestamp; }
}
