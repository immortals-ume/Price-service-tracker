package com.fintech.pricetracking.strategy;

import com.fintech.pricetracking.model.PriceRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy that selects the price record with the most recent asOf timestamp
 * for each instrument.
 * <p>
 * REQUIREMENT: "The last value is determined by the asOf time, as set by the producer"
 * <p>
 * This strategy implements the core business logic for determining which price
 * is "latest" when multiple records exist for the same instrument.
 */
public class LatestAsOfPriceSelectionStrategy implements PriceSelectionStrategy {
    
    /**
     * Selects the latest price for each instrument based on asOf timestamp.
     * <p>
     * Algorithm:
     * 1. Iterate through all records in the batch (from all chunks)
     * 2. For each instrument, compare asOf timestamps
     * 3. Keep the record with the most recent (latest) asOf time
     * 4. Return map of instrumentId -> latest PriceRecord
     * <p>
     * Edge cases handled:
     * - Multiple records for same instrument: Latest asOf wins
     * - Records uploaded in non-chronological order: Still selects correct latest
     * - Same asOf timestamp: Last one processed wins (deterministic)
     * 
     * @param records all price records from a batch (from all chunks)
     * @return map of instrument ID to the latest price record
     */
    @Override
    public Map<String, PriceRecord> selectPrices(List<PriceRecord> records) {
        Map<String, PriceRecord> latestPrices = new HashMap<>();

        for (PriceRecord record : records) {
            String instrumentId = record.id();
            latestPrices.compute(instrumentId, (key, existing) -> {
                if (existing == null) {
                    return record;
                }
                if (record.asOf().isAfter(existing.asOf())) {
                    return record;
                }
                return existing;
            });
        }
        
        return latestPrices;
    }
}
