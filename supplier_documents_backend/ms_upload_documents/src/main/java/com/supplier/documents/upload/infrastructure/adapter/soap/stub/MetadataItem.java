package com.supplier.documents.upload.infrastructure.adapter.soap.stub;

import jakarta.xml.bind.annotation.*;

/**
 * Metadata Item.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MetadataItem", propOrder = {
    "key",
    "value"
})
public class MetadataItem {

    @XmlElement(name = "Key", required = true)
    private String key;

    @XmlElement(name = "Value", required = true)
    private String value;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
