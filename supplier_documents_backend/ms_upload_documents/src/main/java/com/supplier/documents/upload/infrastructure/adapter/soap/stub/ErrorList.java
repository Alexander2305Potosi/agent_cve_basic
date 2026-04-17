package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;
import java.util.List;

/**
 * Error List.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ErrorList", propOrder = {
    "error"
})
public class ErrorList {

    @XmlElement(name = "Error")
    private List<ErrorType> error;

    public List<ErrorType> getError() {
        return error;
    }

    public void setError(List<ErrorType> error) {
        this.error = error;
    }
}
