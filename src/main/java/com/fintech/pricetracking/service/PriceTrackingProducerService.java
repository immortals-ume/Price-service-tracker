package com.fintech.pricetracking.service;

import com.fintech.pricetracking.batch.BatchManager;
import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.exception.InvalidBatchOperationException;
import com.fintech.pricetracking.exception.InvalidChunkSizeException;
import com.fintech.pricetracking.model.BatchAudit;
import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.PriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Producer service implementation for publishing price data in batch runs.
 * 
 * <p>This class implements ONLY producer operations, following the
 * Interface Segregation Principle (ISP). Producers don't need to know
 * about consumer operations.
 * 
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Manage batch lifecycle (start, upload, complete, cancel)</li>
 *   <li>Validate chunk sizes and batch operations</li>
 *   <li>Coordinate with BatchManager and PriceRepository</li>
 * </ul>
 *
 */
public class PriceTrackingProducerService implements ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(PriceTrackingProducerService.class);
    
    private final BatchManager batchManager;
    private final PriceRepository priceRepository;

    /**
     * Constructs a new PriceTrackingProducerService with injected dependencies.
     * 
     * @param batchManager manages batch lifecycle
     * @param priceRepository handles price data storage
     * @throws NullPointerException if any parameter is null
     */
    public PriceTrackingProducerService(
            BatchManager batchManager,
            PriceRepository priceRepository) {
        this.batchManager = Objects.requireNonNull(batchManager, "batchManager cannot be null");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository cannot be null");
    }

    @Override
    public String startBatch() {
        String batchId = batchManager.createBatch();
        logger.info("Batch started: {}", batchId);
        return batchId;
    }

    /**
     * Uploads a chunk of price records to an in-progress batch.
     * 
     * <p><b>REQUIREMENT:</b> "The producer uploads the records in the batch run in multiple chunks of 1000 records"
     * 
     * <p> Records go to STAGING, NOT visible to consumers yet!
     * 
     * @param batchId the batch identifier
     * @param records the list of price records (max 1000 per chunk)
     * @throws IllegalArgumentException if records is null
     * @throws InvalidChunkSizeException if chunk exceeds 1000 records
     * @throws InvalidBatchOperationException if batch not found
     */
    @Override
    public void uploadChunk(String batchId, List<PriceRecord> records) {
        if (records == null) {
            logger.error("Upload chunk failed for batch {}: records is null", batchId);
            throw new IllegalArgumentException("records must not be null");
        }
        
        if (records.size() > 1000) {
            logger.error("Upload chunk failed for batch {}: chunk size {} exceeds maximum of 1000", 
                batchId, records.size());
            throw new InvalidChunkSizeException("Chunk size exceeds maximum of 1000 records: " + records.size());
        }
        
        if (!batchManager.batchExists(batchId)) {
            logger.error("Upload chunk failed: batch {} not found", batchId);
            throw new InvalidBatchOperationException("Batch not found: " + batchId);
        }
        
        batchManager.addRecords(batchId, records);
        logger.debug("Uploaded chunk to batch {}: {} records", batchId, records.size());
    }

    /**
     * Completes a batch and makes all prices available to consumers atomically.
     * 
     * <p><b>REQUIREMENT:</b> "On completion, all prices in a batch run should be made available at the same time"
     * <p><b>REQUIREMENT:</b> "The last value is determined by the asOf time"
     * 
     * <p><b>Implementation:</b> Saves ALL records to history without filtering.
     * The repository maintains full audit trail. Consumers automatically get the latest
     * price by asOf time when querying.
     * 
     * @param batchId the batch identifier to complete
     * @throws InvalidBatchOperationException if batch not found
     */
    @Override
    public void completeBatch(String batchId) {
        if (!batchManager.batchExists(batchId)) {
            logger.error("Complete batch failed: batch {} not found", batchId);
            throw new InvalidBatchOperationException("Batch not found: " + batchId);
        }
        List<PriceRecord> records = batchManager.getBatchRecords(batchId);
        logger.info("Completing batch {}: {} total records", batchId, records.size());
        
        priceRepository.saveAllRecords(records);
        batchManager.removeBatch(batchId);
        
        logger.info("Batch completed successfully: {}", batchId);
    }

    /**
     * Cancels a batch and discards all uploaded records.
     * 
     * <p><b>REQUIREMENT:</b> "Batch runs which are cancelled can be discarded"
     * 
     * @param batchId the batch identifier to cancel
     * @throws InvalidBatchOperationException if batch not found
     */
    @Override
    public void cancelBatch(String batchId) {
        if (!batchManager.batchExists(batchId)) {
            logger.error("Cancel batch failed: batch {} not found", batchId);
            throw new InvalidBatchOperationException("Batch not found: " + batchId);
        }
        
        logger.info("Cancelling batch: {}", batchId);
        
        if (batchManager instanceof InMemoryBatchManager) {
            ((InMemoryBatchManager) batchManager)
                .removeBatch(batchId, BatchAudit.BatchStatus.CANCELLED);
        } else {
            batchManager.removeBatch(batchId);
        }
        
        logger.info("Batch cancelled: {}", batchId);
    }
}
