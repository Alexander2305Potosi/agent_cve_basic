package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Document Submission Response.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "responseHeader",
    "confirmationData"
})
@XmlRootElement(name = "DocumentSubmissionResponse", namespace = "http://supplier.com/document-service")
public class DocumentSubmissionResponse {

    @XmlElement(name = "ResponseHeader", required = true)
    private ResponseHeader responseHeader;

    @XmlElement(name = "ConfirmationData")
    private ConfirmationData confirmationData;

    public ResponseHeader getResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(ResponseHeader responseHeader) {
        this.responseHeader = responseHeader;
    }

    public ConfirmationData getConfirmationData() {
        return confirmationData;
    }

    public void setConfirmationData(ConfirmationData confirmationData) {
        this.confirmationData = confirmationData;
    }
}
