package com.gds.common;

/** The request is valid but can't be done in the current state, e.g. seats sold out or PNR already ticketed. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
