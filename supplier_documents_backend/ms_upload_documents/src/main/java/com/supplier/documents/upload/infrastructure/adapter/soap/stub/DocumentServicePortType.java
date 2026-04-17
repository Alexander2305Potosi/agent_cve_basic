package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

/**
 * Document Service Port Type - SOAP interface stub.
 * Generated from external-document-service.wsdl
 */
@WebService(name = "DocumentServicePortType", targetNamespace = "http://supplier.com/document-service")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.BARE)
public interface DocumentServicePortType {

    /**
     * Submit a document to the external service.
     * @param request the document submission request
     * @return the document submission response
     */
    @WebMethod(operationName = "SubmitDocument", action = "http://supplier.com/document-service/SubmitDocument")
    @WebResult(name = "DocumentSubmissionResponse", targetNamespace = "http://supplier.com/document-service", partName = "response")
    DocumentSubmissionResponse submitDocument(
            @WebParam(name = "DocumentSubmissionRequest", targetNamespace = "http://supplier.com/document-service", partName = "request")
            DocumentSubmissionRequest request);

    /**
     * Get the status of a submitted document.
     * @param request the document status request
     * @return the document status response
     */
    @WebMethod(operationName = "GetDocumentStatus", action = "http://supplier.com/document-service/GetDocumentStatus")
    @WebResult(name = "DocumentStatusResponse", targetNamespace = "http://supplier.com/document-service", partName = "response")
    DocumentStatusResponse getDocumentStatus(
            @WebParam(name = "DocumentStatusRequest", targetNamespace = "http://supplier.com/document-service", partName = "request")
            DocumentStatusRequest request);
}
