package com.fintech.pricetracking.exception;

/**
 * Exception thrown when a chunk upload exceeds the maximum allowed size of 1000 records.
 */
public class InvalidChunkSizeException extends RuntimeException {
    
    public InvalidChunkSizeException(String message) {
        super(message);
    }
    
    public InvalidChunkSizeException(String message, Throwable cause) {
        super(message, cause);
    }
}
