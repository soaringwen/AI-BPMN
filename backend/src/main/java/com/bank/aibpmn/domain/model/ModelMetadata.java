package com.bank.aibpmn.domain.model;

public class ModelMetadata {

    private String createdBy;
    private String generationPrompt;

    public ModelMetadata() {
    }

    public ModelMetadata(String createdBy, String generationPrompt) {
        this.createdBy = createdBy;
        this.generationPrompt = generationPrompt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getGenerationPrompt() {
        return generationPrompt;
    }

    public void setGenerationPrompt(String generationPrompt) {
        this.generationPrompt = generationPrompt;
    }
}
