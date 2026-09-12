package com.bank.aibpmn.domain.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {

    private List<ValidationIssue> errors = new ArrayList<>();
    private List<ValidationIssue> warnings = new ArrayList<>();

    public ValidationResult() {
    }

    public static ValidationResult empty() {
        return new ValidationResult();
    }

    public void add(ValidationIssue issue) {
        if (issue.getSeverity() == Severity.ERROR) {
            errors.add(issue);
        } else {
            warnings.add(issue);
        }
    }

    public void merge(ValidationResult other) {
        errors.addAll(other.errors);
        warnings.addAll(other.warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ValidationIssue> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationIssue> errors) {
        this.errors = errors;
    }

    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ValidationIssue> warnings) {
        this.warnings = warnings;
    }
}
