package com.bank.aibpmn.domain.model;

public class Pool {

    private String id;
    private String name;
    private String processRef;

    public Pool() {
    }

    public Pool(String id, String name, String processRef) {
        this.id = id;
        this.name = name;
        this.processRef = processRef;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProcessRef() {
        return processRef;
    }

    public void setProcessRef(String processRef) {
        this.processRef = processRef;
    }
}
