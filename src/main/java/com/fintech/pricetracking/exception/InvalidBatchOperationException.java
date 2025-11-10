package com.fintech.pricetracking.exception;

/**
 * Exception thrown when batch operations are called in incorrect order or on invalid batches.
 * Examples include uploading to a non-existent batch, completing a non-existent batch,
 * or canceling a non-existent batch.
 */
public class InvalidBatchOperationException extends RuntimeException {
    
    public InvalidBatchOperationException(String message) {
        super(message);
    }
    
    public InvalidBatchOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
