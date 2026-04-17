package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Request Header.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RequestHeader", propOrder = {
    "transactionId",
    "timestamp",
    "sourceSystem",
    "operationType"
})
public class RequestHeader {

    @XmlElement(name = "TransactionId", required = true)
    private String transactionId;

    @XmlElement(name = "Timestamp", required = true)
    private String timestamp;

    @XmlElement(name = "SourceSystem", required = true)
    private String sourceSystem;

    @XmlElement(name = "OperationType", required = true)
    private String operationType;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
}
