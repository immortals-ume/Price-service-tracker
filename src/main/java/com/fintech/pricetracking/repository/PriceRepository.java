package com.fintech.pricetracking.repository;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.Optional;

/**
 * Repository interface for price data storage operations.
 */
public interface PriceRepository {
    
    /**
     * Saves all price records to history without filtering.
     * Each record is added to its instrument's history list.
     * 
     * @param records list of all price records to save
     */
    void saveAllRecords(java.util.List<PriceRecord> records);
    
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
