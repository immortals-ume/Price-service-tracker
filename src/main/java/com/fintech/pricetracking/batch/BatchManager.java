package com.fintech.pricetracking.batch;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.List;

/**
 * Interface for managing batch operations.
 */
public interface BatchManager {
    
    /**
     * Creates a new batch and returns its unique identifier.
     * 
     * @return unique batch ID
     */
    String createBatch();
    
    /**
     * Adds records to an existing batch.
     * 
     * @param batchId the batch identifier
     * @param records the records to add
     */
    void addRecords(String batchId, List<PriceRecord> records);
    
    /**
     * Retrieves all records from a batch.
     * 
     * @param batchId the batch identifier
     * @return list of all records in the batch
     */
    List<PriceRecord> getBatchRecords(String batchId);
    
    /**
     * Removes a batch from management.
     * 
     * @param batchId the batch identifier
     */
    void removeBatch(String batchId);
    
    /**
     * Removes a batch from management with a specific status.
     * 
     * @param batchId the batch identifier
     * @param cancelled true if batch was cancelled, false if completed
     */
    void removeBatch(String batchId, boolean cancelled);
    
    /**
     * Checks if a batch exists.
     * 
     * @param batchId the batch identifier
     * @return true if batch exists, false otherwise
     */
    boolean batchExists(String batchId);
}
