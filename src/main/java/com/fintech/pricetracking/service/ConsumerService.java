package com.fintech.pricetracking.service;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.Optional;

/**
 * API for consumers to retrieve price data.
 */
public interface ConsumerService {
    
    /**
     * Returns the most recent price record for an instrument based on asOf time.
     * Returns Optional.empty() if no price exists for the given instrument.
     * 
     * @param instrumentId the instrument identifier
     * @return Optional containing the latest PriceRecord, or empty if not found
     */
    Optional<PriceRecord> getLatestPrice(String instrumentId);
}
