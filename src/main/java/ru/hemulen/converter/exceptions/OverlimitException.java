package ru.hemulen.converter.exceptions;

public class OverlimitException extends Exception {
    public OverlimitException(Throwable cause) {super(cause);}
    public OverlimitException(String message, Throwable cause) {super(message, cause);}

}
