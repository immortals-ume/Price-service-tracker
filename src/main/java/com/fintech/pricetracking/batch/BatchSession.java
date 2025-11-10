package com.fintech.pricetracking.batch;

import com.fintech.pricetracking.model.PriceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a batch session that tracks the state of an in-progress batch run.
 * 
 * <p>This class encapsulates the data and behavior of a single batch session,
 * following the Single Responsibility Principle (SRP) by managing only
 * batch session state and operations.
 * 
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Store batch metadata (ID, creation time)</li>
 *   <li>Accumulate price records from multiple chunks</li>
 *   <li>Provide immutable access to accumulated records</li>
 * </ul>
 * 
 * <h2>Thread Safety:</h2>
 * <p>This class is NOT thread-safe by design. Thread safety is managed
 * by the BatchManager that uses this class. This follows the principle
 * of separating concerns - BatchSession manages data, BatchManager manages concurrency.
 * 
 * <h2>Immutability:</h2>
 * <ul>
 *   <li>batchId and createdAt are final and immutable</li>
 *   <li>records list is mutable internally but exposed as unmodifiable</li>
 *   <li>getAllRecords() returns an unmodifiable view</li>
 * </ul>
 */
public final class BatchSession {
    
    private final String batchId;
    private final Instant createdAt;
    private final List<PriceRecord> records;
    
    /**
     * Constructs a new BatchSession with the given batch ID.
     * 
     * @param batchId the unique identifier for this batch session
     * @throws NullPointerException if batchId is null
     */
    public BatchSession(String batchId) {
        this.batchId = Objects.requireNonNull(batchId, "batchId cannot be null");
        this.createdAt = Instant.now();
        this.records = new ArrayList<>();
    }
    
    /**
     * Adds a list of price records to this batch session.
     * 
     * <p>Records are accumulated from multiple chunks uploaded during
     * the batch run. All records are stored in the order they are added.
     * 
     * @param newRecords the list of price records to add
     * @throws NullPointerException if newRecords is null
     */
    public void addRecords(List<PriceRecord> newRecords) {
        Objects.requireNonNull(newRecords, "newRecords cannot be null");
        records.addAll(newRecords);
    }
    
    /**
     * Returns an unmodifiable view of all records in this batch session.
     * 
     * <p>The returned list is immutable and reflects all records added
     * via addRecords() up to this point. This ensures encapsulation
     * and prevents external modification of internal state.
     * 
     * @return unmodifiable list of all price records in this batch
     */
    public List<PriceRecord> getAllRecords() {
        return Collections.unmodifiableList(records);
    }
    
    /**
     * Returns the unique identifier of this batch session.
     * 
     * @return the batch ID
     */
    public String getBatchId() {
        return batchId;
    }
    
    /**
     * Returns the timestamp when this batch session was created.
     * 
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Returns the current number of records in this batch session.
     * 
     * <p>This is useful for monitoring and debugging purposes.
     * 
     * @return the number of records accumulated so far
     */
    public int getRecordCount() {
        return records.size();
    }
    
    @Override
    public String toString() {
        return "BatchSession{" +
                "batchId='" + batchId + '\'' +
                ", createdAt=" + createdAt +
                ", recordCount=" + records.size() +
                '}';
    }
}
