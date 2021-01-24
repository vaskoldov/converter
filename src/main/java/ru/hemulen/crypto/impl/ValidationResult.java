package ru.hemulen.crypto.impl;

import java.security.cert.X509Certificate;

public class ValidationResult {
    private X509Certificate certificate;
    private boolean valid;
    private String error;
    private Exception exception;

    public ValidationResult() {
    }

    public X509Certificate getCertificate() {
        return this.certificate;
    }

    public void setCertificate(X509Certificate certificate) {
        this.certificate = certificate;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getError() {
        return this.error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Exception getException() {
        return this.exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public String toString() {
        return "\nValidationResult{certificate=" + this.certificate + ", valid=" + this.valid + ", error='" + this.error + '\'' + ", exception=" + this.exception + '}';
    }

}
