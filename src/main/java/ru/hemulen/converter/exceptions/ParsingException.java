package ru.hemulen.converter.exceptions;

public class ParsingException extends Exception {
    public ParsingException(Throwable cause) {super(cause);}
    public ParsingException(String message, Throwable cause) {super(message, cause);}
}
