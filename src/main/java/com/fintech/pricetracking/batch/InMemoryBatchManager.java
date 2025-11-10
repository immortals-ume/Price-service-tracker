package com.fintech.pricetracking.batch;

import com.fintech.pricetracking.model.BatchAudit;
import com.fintech.pricetracking.model.PriceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory implementation of BatchManager with improved concurrency control.
 * 
 * <p>This class manages batch sessions with thread-safe operations,
 * following the Single Responsibility Principle (SRP) by focusing
 * solely on batch lifecycle management.
 * 
 * <h2>Design Principles Applied:</h2>
 * <ul>
 *   <li><b>SRP:</b> Manages only batch lifecycle (create, add, remove, query)</li>
 *   <li><b>DIP:</b> Implements BatchManager interface for loose coupling</li>
 *   <li><b>OCP:</b> Closed for modification, open for extension via interface</li>
 * </ul>
 * 
 * <h2>Concurrency Pattern: ReadWriteLock</h2>
 * <ul>
 *   <li>Uses {@link ReentrantReadWriteLock} for better concurrency</li>
 *   <li>Multiple threads can read batch state concurrently (batchExists, getBatchRecords)</li>
 *   <li>Write operations (create, add, remove) acquire exclusive write lock</li>
 *   <li>Better performance than synchronized for read-heavy workloads</li>
 * </ul>
 * 
 * <h2>Thread Safety:</h2>
 * <ul>
 *   <li>Uses ConcurrentHashMap for lock-free reads when possible</li>
 *   <li>ReadWriteLock ensures consistency for compound operations</li>
 *   <li>Ensures atomic batch operations</li>
 * </ul>
 */
public final class InMemoryBatchManager implements BatchManager {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InMemoryBatchManager.class);
    
    private final Map<String, BatchSession> batches;
    private final List<BatchAudit> batchHistory;
    private final ReadWriteLock lock;
    
    /**
     * Constructs a new InMemoryBatchManager with empty batch storage.
     */
    public InMemoryBatchManager() {
        this.batches = new ConcurrentHashMap<>();
        this.batchHistory = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Creates a new batch session with a unique identifier.
     * 
     * <p><b>Thread Safety:</b> Uses write lock to ensure atomic creation.
     * 
     * @return unique batch identifier (UUID)
     */
    @Override
    public String createBatch() {
        String batchId = UUID.randomUUID().toString();
        lock.writeLock().lock();
        try {
            batches.put(batchId, new BatchSession(batchId));
            logger.debug("Created batch session: {}", batchId);
            return batchId;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Adds price records to an existing batch session.
     * 
     * <p><b>Thread Safety:</b> Uses write lock to ensure atomic addition.
     * 
     * @param batchId the batch identifier
     * @param records the list of price records to add
     * @throws IllegalArgumentException if batch not found
     */
    @Override
    public void addRecords(String batchId, List<PriceRecord> records) {
        lock.writeLock().lock();
        try {
            BatchSession session = batches.get(batchId);
            if (session == null) {
                logger.error("Cannot add records: batch {} not found", batchId);
                throw new IllegalArgumentException("Batch not found: " + batchId);
            }
            session.addRecords(records);
            logger.debug("Added {} records to batch {}. Total: {}", 
                records.size(), batchId, session.getRecordCount());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Retrieves all records from a batch session.
     * 
     * <p><b>Thread Safety:</b> Uses read lock to allow concurrent reads.
     * 
     * @param batchId the batch identifier
     * @return unmodifiable list of all records in the batch
     * @throws IllegalArgumentException if batch not found
     */
    @Override
    public List<PriceRecord> getBatchRecords(String batchId) {
        lock.readLock().lock();
        try {
            BatchSession session = batches.get(batchId);
            if (session == null) {
                throw new IllegalArgumentException("Batch not found: " + batchId);
            }
            return session.getAllRecords();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Removes a batch session from storage and records completion in history.
     * 
     * <p><b>Thread Safety:</b> Uses write lock to ensure atomic removal.
     * 
     * @param batchId the batch identifier to remove
     * @param status the completion status (COMPLETED or CANCELLED)
     */
    public void removeBatch(String batchId, BatchAudit.BatchStatus status) {
        lock.writeLock().lock();
        try {
            BatchSession session = batches.remove(batchId);
            if (session != null) {
                int recordCount = session.getAllRecords().size();
                batchHistory.add(new BatchAudit(batchId, Instant.now(), recordCount, status));
                logger.info("Removed batch {}: status={}, records={}", batchId, status, recordCount);
            } else {
                logger.warn("Attempted to remove non-existent batch: {}", batchId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes a batch session from storage (legacy method).
     * Records as COMPLETED by default.
     * 
     * <p><b>Thread Safety:</b> Uses write lock to ensure atomic removal.
     * 
     * @param batchId the batch identifier to remove
     */
    @Override
    public void removeBatch(String batchId) {
        removeBatch(batchId, BatchAudit.BatchStatus.COMPLETED);
    }
    
    /**
     * Retrieves the complete batch history.
     * 
     * @return unmodifiable list of all batch audit records
     */
    public List<BatchAudit> getBatchHistory() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(batchHistory));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if a batch session exists.
     * 
     * <p><b>Thread Safety:</b> Uses read lock to allow concurrent checks.
     * 
     * @param batchId the batch identifier to check
     * @return true if batch exists, false otherwise
     */
    @Override
    public boolean batchExists(String batchId) {
        lock.readLock().lock();
        try {
            return batches.containsKey(batchId);
        } finally {
            lock.readLock().unlock();
        }
    }
}
