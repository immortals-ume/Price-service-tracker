package com.fintech.pricetracking.service;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.List;

/**
 * API for producers to publish price data in batches.
 */
public interface ProducerService {
    
    /**
     * Creates a new batch session and returns a unique batch ID.
     * 
     * @return unique batch identifier
     */
    String startBatch();
    
    /**
     * Adds records to an in-progress batch (max 1000 per call).
     * 
     * @param batchId the batch identifier
     * @param records the list of price records to upload
     * @throws com.fintech.pricetracking.exception.InvalidBatchOperationException if batch not found
     * @throws com.fintech.pricetracking.exception.InvalidChunkSizeException if chunk exceeds 1000 records
     */
    void uploadChunk(String batchId, List<PriceRecord> records);
    
    /**
     * Atomically publishes all records from the batch.
     * 
     * @param batchId the batch identifier
     * @throws com.fintech.pricetracking.exception.InvalidBatchOperationException if batch not found
     */
    void completeBatch(String batchId);
    
    /**
     * Discards all records from the batch.
     * 
     * @param batchId the batch identifier
     * @throws com.fintech.pricetracking.exception.InvalidBatchOperationException if batch not found
     */
    void cancelBatch(String batchId);
}
