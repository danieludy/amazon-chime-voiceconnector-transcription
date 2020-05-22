package com.amazonaws.kvstranscribestreaming.streaming.exception;

public class StreamingStatusValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StreamingStatusValidationException(String message) {
        super(message);
    }
}