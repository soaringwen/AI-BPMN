package com.bank.aibpmn.domain.diff;

public class ElementChange {

    private String elementId;
    private String elementType;
    private String name;

    public ElementChange() {
    }

    public ElementChange(String elementId, String elementType, String name) {
        this.elementId = elementId;
        this.elementType = elementType;
        this.name = name;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
