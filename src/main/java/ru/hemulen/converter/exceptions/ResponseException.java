package ru.hemulen.converter.exceptions;

public class ResponseException extends Exception {
    public ResponseException(Throwable cause) {super(cause);}
    public ResponseException(String message, Throwable cause) {super(message, cause);}
}
