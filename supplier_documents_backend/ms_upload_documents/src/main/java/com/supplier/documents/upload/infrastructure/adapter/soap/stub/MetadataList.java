package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;
import java.util.List;

/**
 * Metadata List.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MetadataList", propOrder = {
    "metadataItem"
})
public class MetadataList {

    @XmlElement(name = "MetadataItem")
    private List<MetadataItem> metadataItem;

    public List<MetadataItem> getMetadataItem() {
        return metadataItem;
    }

    public void setMetadataItem(List<MetadataItem> metadataItem) {
        this.metadataItem = metadataItem;
    }
}
