package com.fintech.pricetracking.repository;

import com.fintech.pricetracking.model.PriceRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of PriceRepository using thread-safe storage.
 * 
 * <p>This class implements the Repository Pattern for data access abstraction.
 * It provides thread-safe operations for storing and retrieving price records.
 * 
 * <h2>Thread Safety Strategy - ReadWriteLock Pattern:</h2>
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for underlying storage</li>
 *   <li>Uses {@link java.util.concurrent.locks.ReentrantReadWriteLock} for concurrency control</li>
 *   <li>Multiple readers can access concurrently without blocking each other</li>
 *   <li>Writers acquire exclusive lock to ensure atomicity</li>
 *   <li>Better performance than synchronized for read-heavy workloads</li>
 * </ul>
 * 
 * <h2>Storage Architecture:</h2>
 * <pre>
 * Storage: Map&lt;InstrumentId, List&lt;PriceRecord&gt;&gt;
 * - Key: Instrument identifier (e.g., "AAPL", "GOOGL")
 * - Value: List of ALL price records sorted by asOf time (newest first)
 * - Stores FULL HISTORY of all price records per instrument
 * - Cross-batch updates: adds new prices, maintains sorted order
 * - Consumers get latest (first in list) by default
 * - History available for auditing, analysis, debugging
 * </pre>
 * 
 * <h2>Separation from Staging</h2>
 * <p>This repository contains ONLY completed batch prices.
 * In-progress batches are stored separately in BatchManager.
 * This separation ensures consumers never see partial batches.
 *
 */
public final class InMemoryPriceRepository implements PriceRepository {
    
    /**
     * Thread-safe storage for published prices with full history.
     * Contains ALL prices from completed batches.
     * Key: instrument ID, Value: List of price records sorted by asOf (newest first)
     */
    private final ConcurrentHashMap<String, List<PriceRecord>> storage;
    
    /**
     * ReadWriteLock for improved concurrency control.
     * Allows multiple concurrent reads while ensuring exclusive writes.
     */
    private final java.util.concurrent.locks.ReadWriteLock lock;
    
    /**
     * Constructs a new InMemoryPriceRepository with empty storage.
     */
    public InMemoryPriceRepository() {
        this.storage = new ConcurrentHashMap<>();
        this.lock = new java.util.concurrent.locks.ReentrantReadWriteLock();
    }
    
    /**
     * Adds a price record to the history list, maintaining sorted order (newest first).
     * 
     * @param existingHistory the existing list of prices (may be null)
     * @param newPrice the new price to add
     * @return updated list with new price inserted in correct position
     */
    private List<PriceRecord> addToHistory(List<PriceRecord> existingHistory, PriceRecord newPrice) {
        if (existingHistory == null) {
            List<PriceRecord> newList = new ArrayList<>();
            newList.add(newPrice);
            return newList;
        }
        List<PriceRecord> updatedList = new ArrayList<>(existingHistory);
        int insertIndex = 0;
        for (int i = 0; i < updatedList.size(); i++) {
            if (newPrice.asOf().isAfter(updatedList.get(i).asOf())) {
                insertIndex = i;
                break;
            }
            insertIndex = i + 1;
        }
        
        updatedList.add(insertIndex, newPrice);
        return updatedList;
    }
    
    /**
     * Atomically saves all prices to the repository, maintaining full history.
     * 
     * <p><b>REQUIREMENT:</b> "On completion, all prices in a batch run should be 
     * made available at the same time"
     * 
     * <p><b>Implementation:</b>
     * <ul>
     *   <li>Uses write lock to ensure atomicity</li>
     *   <li>Adds each price to the instrument's history list</li>
     *   <li>Maintains sorted order (newest first)</li>
     *   <li>Consumers see either none or all of the batch prices atomically</li>
     *   <li>No partial visibility possible</li>
     * </ul>
     * 
     * <p><b>Cross-Batch Behavior (with history):</b>
     * <ul>
     *   <li>Batch 1: AAPL @ 10:05 AM → stored [10:05]</li>
     *   <li>Batch 2: AAPL @ 09:00 AM → added to history [10:05, 09:00]</li>
     *   <li>Batch 3: AAPL @ 10:10 AM → added to history [10:10, 10:05, 09:00]</li>
     *   <li>Consumer gets: AAPL @ 10:10 AM (latest, first in list)</li>
     * </ul>
     * 
     * <p><b>Thread Safety:</b> Uses write lock to prevent concurrent modifications.
     * Multiple saveAll() calls are serialized. Read operations can proceed concurrently.
     * 
     * <p><b>Performance:</b> O(n * m) where n = number of prices, m = avg history size per instrument.
     * 
     * @param prices map of instrument ID to price record (already filtered to latest by asOf within batch)
     * @throws NullPointerException if prices is null (Java 17 Objects.requireNonNull)
     */
    @Override
    public void saveAll(Map<String, PriceRecord> prices) {
        Objects.requireNonNull(prices, "prices map cannot be null");
        
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, PriceRecord> entry : prices.entrySet()) {
                String instrumentId = entry.getKey();
                PriceRecord newPrice = entry.getValue();
                
                storage.compute(instrumentId, (key, existingHistory) -> 
                    addToHistory(existingHistory, newPrice)
                );
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Retrieves the latest price record by instrument ID.
     * 
     * <p><b>REQUIREMENT:</b> "Consumers can request the last price record for a given id"
     * 
     * <p><b>REQUIREMENT:</b> "The last value is determined by the asOf time"
     * 
     * <p><b>How "latest by asOf" works with history:</b>
     * <ol>
     *   <li>Storage maintains full history sorted by asOf (newest first)</li>
     *   <li>This method returns the FIRST element in the list (latest)</li>
     *   <li>Fast: O(1) lookup + O(1) list access</li>
     *   <li>Consumer always gets the most recent price by asOf time</li>
     * </ol>
     * 
     * <p><b>Thread Safety:</b> Uses read lock allowing multiple concurrent reads.
     * Blocks only when a write operation is in progress.
     * 
     * <p><b>Performance:</b> O(1) HashMap lookup + O(1) list access.
     * 
     * @param instrumentId the instrument identifier (must not be null)
     * @return Optional containing the latest price record, or empty if not found
     * @throws NullPointerException if instrumentId is null
     */
    @Override
    public Optional<PriceRecord> findByInstrumentId(String instrumentId) {
        Objects.requireNonNull(instrumentId, "instrumentId cannot be null");
        
        lock.readLock().lock();
        try {
            List<PriceRecord> history = storage.get(instrumentId);
            if (history == null || history.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(history.get(0));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Retrieves the complete price history for a given instrument.
     * 
     * <p><b> Returns all historical prices, sorted by asOf (newest first).
     * 
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Historical analysis and reporting</li>
     *   <li>Auditing and compliance</li>
     *   <li>Price trend analysis</li>
     *   <li>Debugging and troubleshooting</li>
     * </ul>
     * 
     * <p><b>Thread Safety:</b> Uses read lock allowing multiple concurrent reads.
     * 
     * @param instrumentId the instrument identifier
     * @return unmodifiable list of all price records, sorted by asOf (newest first), or empty list if not found
     * @throws NullPointerException if instrumentId is null
     */
    public List<PriceRecord> findHistoryByInstrumentId(String instrumentId) {
        Objects.requireNonNull(instrumentId, "instrumentId cannot be null");
        
        lock.readLock().lock();
        try {
            List<PriceRecord> history = storage.get(instrumentId);
            if (history == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(history);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clears all stored prices from the repository.
     * 
     * <p><b>Use Case:</b> Testing, cleanup, or reset operations.
     * 
     * <p><b>Thread Safety:</b> Uses write lock to prevent concurrent access during clear.
     * 
     * <p><b>Warning:</b> This operation removes ALL prices and history. Use with caution.
     */
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            storage.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Returns the current number of instruments with prices in the repository.
     * 
     * <p><b>Use Case:</b> Monitoring, metrics, debugging.
     * 
     * <p><b>Thread Safety:</b> ConcurrentHashMap.size() is thread-safe.
     * 
     * @return number of instruments with stored prices
     */
    public int size() {
        return storage.size();
    }
    
    /**
     * Checks if the repository contains a price for the given instrument.
     * 
     * <p><b>Thread Safety:</b> ConcurrentHashMap.containsKey() is thread-safe.
     * 
     * @param instrumentId the instrument identifier
     * @return true if a price exists for this instrument, false otherwise
     */
    public boolean contains(String instrumentId) {
        return storage.containsKey(instrumentId);
    }
}
