package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Response Header.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResponseHeader", propOrder = {
    "success",
    "timestamp",
    "errors"
})
public class ResponseHeader {

    @XmlElement(name = "Success")
    private boolean success;

    @XmlElement(name = "Timestamp", required = true)
    private String timestamp;

    @XmlElement(name = "Errors")
    private ErrorList errors;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public ErrorList getErrors() {
        return errors;
    }

    public void setErrors(ErrorList errors) {
        this.errors = errors;
    }
}
