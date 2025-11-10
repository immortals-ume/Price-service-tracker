package com.fintech.pricetracking.strategy;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for selecting prices from a list of records.
 * Follows Strategy Pattern for flexible price selection algorithms.
 */
public interface PriceSelectionStrategy {
    
    /**
     * Selects the appropriate price record for each instrument.
     * 
     * @param records list of price records
     * @return map of instrument ID to selected price record
     */
    Map<String, PriceRecord> selectPrices(List<PriceRecord> records);
}
