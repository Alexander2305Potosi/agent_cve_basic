package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Document Status Response.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "responseHeader",
    "statusData"
})
@XmlRootElement(name = "DocumentStatusResponse", namespace = "http://supplier.com/document-service")
public class DocumentStatusResponse {

    @XmlElement(name = "ResponseHeader", required = true)
    private ResponseHeader responseHeader;

    @XmlElement(name = "StatusData")
    private StatusData statusData;

    public ResponseHeader getResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(ResponseHeader responseHeader) {
        this.responseHeader = responseHeader;
    }

    public StatusData getStatusData() {
        return statusData;
    }

    public void setStatusData(StatusData statusData) {
        this.statusData = statusData;
    }
}
