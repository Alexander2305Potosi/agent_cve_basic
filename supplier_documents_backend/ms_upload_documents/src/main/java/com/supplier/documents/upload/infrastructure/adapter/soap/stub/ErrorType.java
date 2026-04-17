package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Error Type.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ErrorType", propOrder = {
    "errorCode",
    "errorMessage",
    "errorDetail"
})
public class ErrorType {

    @XmlElement(name = "ErrorCode", required = true)
    private String errorCode;

    @XmlElement(name = "ErrorMessage", required = true)
    private String errorMessage;

    @XmlElement(name = "ErrorDetail")
    private String errorDetail;

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }
}
