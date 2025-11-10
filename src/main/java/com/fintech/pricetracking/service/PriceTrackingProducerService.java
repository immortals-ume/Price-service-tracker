package com.fintech.pricetracking.service;

import com.fintech.pricetracking.batch.BatchManager;
import com.fintech.pricetracking.exception.InvalidBatchOperationException;
import com.fintech.pricetracking.exception.InvalidChunkSizeException;
import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.PriceRepository;
import com.fintech.pricetracking.strategy.PriceSelectionStrategy;

import java.util.List;
import java.util.Map;
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
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li><b>SRP:</b> Handles only producer operations</li>
 *   <li><b>ISP:</b> Implements only ProducerService interface</li>
 *   <li><b>DIP:</b> Depends on abstractions (interfaces)</li>
 * </ul>
 */
public class PriceTrackingProducerService implements ProducerService {

    private final BatchManager batchManager;
    private final PriceRepository priceRepository;
    private final PriceSelectionStrategy priceSelectionStrategy;

    /**
     * Constructs a new PriceTrackingProducerService with injected dependencies.
     * 
     * @param batchManager manages batch lifecycle
     * @param priceRepository handles price data storage
     * @param priceSelectionStrategy determines which prices to select
     * @throws NullPointerException if any parameter is null
     */
    public PriceTrackingProducerService(
            BatchManager batchManager,
            PriceRepository priceRepository,
            PriceSelectionStrategy priceSelectionStrategy) {
        this.batchManager = Objects.requireNonNull(batchManager, "batchManager cannot be null");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository cannot be null");
        this.priceSelectionStrategy = Objects.requireNonNull(priceSelectionStrategy, "priceSelectionStrategy cannot be null");
    }

    @Override
    public String startBatch() {
        return batchManager.createBatch();
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
            throw new IllegalArgumentException("records must not be null");
        }
        
        if (records.size() > 1000) {
            throw new InvalidChunkSizeException("Chunk size exceeds maximum of 1000 records: " + records.size());
        }
        
        if (!batchManager.batchExists(batchId)) {
            throw new InvalidBatchOperationException("Batch not found: " + batchId);
        }
        
        batchManager.addRecords(batchId, records);
    }

    /**
     * Completes a batch and makes all prices available to consumers atomically.
     * 
     * <p><b>REQUIREMENT:</b> "On completion, all prices in a batch run should be made available at the same time"
     * <p><b>REQUIREMENT:</b> "The last value is determined by the asOf time"
     * 
     * @param batchId the batch identifier to complete
     * @throws InvalidBatchOperationException if batch not found
     */
    @Override
    public void completeBatch(String batchId) {
        if (!batchManager.batchExists(batchId)) {
            throw new InvalidBatchOperationException("Batch not found: " + batchId);
        }
        List<PriceRecord> records = batchManager.getBatchRecords(batchId);
        Map<String, PriceRecord> selectedPrices = priceSelectionStrategy.selectPrices(records);
        priceRepository.saveAll(selectedPrices);
        batchManager.removeBatch(batchId);
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
            throw new InvalidBatchOperationException("Batch not found: " + batchId);
        }
        batchManager.removeBatch(batchId);
    }
}
