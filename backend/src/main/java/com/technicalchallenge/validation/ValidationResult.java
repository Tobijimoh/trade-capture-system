package com.technicalchallenge.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple validation result container for collecting business rule errors.
 * Provides static factory helpers and merging capability for multi-step validation.
 */
public class ValidationResult {

    private final List<String> errors = new ArrayList<>();

    /** Returns true if there are no errors */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /** Returns all collected errors (immutable) */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** Adds a single error message */
    public void addError(String error) {
        if (error != null && !error.isBlank()) {
            errors.add(error);
        }
    }

    /** Creates an empty successful validation result */
    public static ValidationResult ok() {
        return new ValidationResult();
    }

    /** Creates an invalid result with a single message */
    public static ValidationResult error(String message) {
        ValidationResult vr = new ValidationResult();
        vr.addError(message);
        return vr;
    }

    /** Merges another ValidationResult's errors into this one */
    public void merge(ValidationResult other) {
        if (other != null && !other.isValid()) {
            errors.addAll(other.getErrors());
        }
    }

    @Override
    public String toString() {
        return isValid()
                ? "ValidationResult: OK"
                : "ValidationResult: " + String.join("; ", errors);
    }
}
