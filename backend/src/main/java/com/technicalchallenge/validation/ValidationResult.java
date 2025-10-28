package com.technicalchallenge.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private boolean valid = true;
    private final List<String> errors = new ArrayList<>();

    public static ValidationResult ok() { return new ValidationResult(); }
    public void addError(String msg) { valid = false; errors.add(msg); }
    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
}
