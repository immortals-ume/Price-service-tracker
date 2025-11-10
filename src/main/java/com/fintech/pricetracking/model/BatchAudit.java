package com.fintech.pricetracking.model;

import java.time.Instant;

/**
 * Audit record for completed batches.
 * 
 * @param batchId unique batch identifier
 * @param completedAt timestamp when batch was completed
 * @param recordCount number of records in the batch
 * @param status batch completion status (COMPLETED or CANCELLED)
 */
public record BatchAudit(
    String batchId,
    Instant completedAt,
    int recordCount,
    BatchStatus status
) {
    public enum BatchStatus {
        COMPLETED,
        CANCELLED
    }
}
