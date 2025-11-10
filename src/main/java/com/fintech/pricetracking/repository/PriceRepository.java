package com.fintech.pricetracking.repository;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for price data storage operations.
 * Follows Repository Pattern for data access abstraction.
 */
public interface PriceRepository {
    
    /**
     * Stores or updates price records atomically.
     * 
     * @param prices map of instrument ID to price record
     */
    void saveAll(Map<String, PriceRecord> prices);
    
    /**
     * Retrieves a price record by instrument ID.
     * 
     * @param instrumentId the instrument identifier
     * @return Optional containing the price record, or empty if not found
     */
    Optional<PriceRecord> findByInstrumentId(String instrumentId);
    
    /**
     * Clears all stored prices.
     */
    void clear();
}
