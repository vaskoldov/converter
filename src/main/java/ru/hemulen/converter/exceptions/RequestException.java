package ru.hemulen.converter.exceptions;

public class RequestException extends Exception {
    public RequestException(Throwable cause) {super(cause);}
    public RequestException(String message, Throwable cause) {super(message, cause);}
}
