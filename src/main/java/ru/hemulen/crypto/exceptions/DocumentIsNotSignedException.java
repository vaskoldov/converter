package ru.hemulen.crypto.exceptions;

public class DocumentIsNotSignedException extends Exception {
    private static final long serialVersionUID = -4790470069490066305L;

    public DocumentIsNotSignedException() {
    }

    public DocumentIsNotSignedException(String message) {
        super(message);
    }
}
