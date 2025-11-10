package com.fintech.pricetracking;

import com.fintech.pricetracking.model.PriceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating test data.
 * Centralizes test data creation to avoid duplication and ensure consistency.
 */
public final class TestDataFactory {
    
    private TestDataFactory() {
        throw new AssertionError("Utility class - do not instantiate");
    }
    
    /**
     * Creates a price record with a past timestamp (realistic).
     * 
     * @param id instrument ID
     * @param secondsAgo how many seconds ago the price was determined
     * @param price the price value
     * @return PriceRecord with past timestamp
     */
    public static PriceRecord createPastPrice(String id, long secondsAgo, double price) {
        return new PriceRecord(id, Instant.now().minusSeconds(secondsAgo), price);
    }
    
    /**
     * Creates a price record relative to a base time (for testing).
     * 
     * @param id instrument ID
     * @param baseTime base timestamp
     * @param secondsAgo seconds before baseTime
     * @param price the price value
     * @return PriceRecord
     */
    public static PriceRecord createPrice(String id, Instant baseTime, long secondsAgo, double price) {
        return new PriceRecord(id, baseTime.minusSeconds(secondsAgo), price);
    }
    
    /**
     * Creates multiple price records for the same instrument with different past timestamps.
     * Useful for testing "latest by asOf" logic.
     * 
     * @param id instrument ID
     * @param baseTime base timestamp
     * @return List of 3 prices: oldest, middle, newest
     */
    public static List<PriceRecord> createMultiplePricesForSameInstrument(String id, Instant baseTime) {
        return List.of(
            createPrice(id, baseTime, 120, 150.0),  
            createPrice(id, baseTime, 60, 152.0),  
            createPrice(id, baseTime, 30, 155.0)  
        );
    }
    
    /**
     * Creates a batch of price records with incrementing past timestamps.
     * 
     * @param prefix instrument ID prefix
     * @param count number of records
     * @param baseTime base timestamp
     * @return List of price records
     */
    public static List<PriceRecord> createBatchPrices(String prefix, int count, Instant baseTime) {
        List<PriceRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(createPrice(prefix + i, baseTime, count - i, 100.0 + i));
        }
        return records;
    }
    
    /**
     * Creates a simple price record (for tests that don't care about timestamp logic).
     * Uses baseTime directly.
     * 
     * @param id instrument ID
     * @param baseTime timestamp
     * @param price the price value
     * @return PriceRecord
     */
    public static PriceRecord createSimplePrice(String id, Instant baseTime, double price) {
        return new PriceRecord(id, baseTime, price);
    }
    
    /**
     * Creates a price record with offset from baseTime (can be positive or negative).
     * For testing relative timestamps.
     * 
     * @param id instrument ID
     * @param baseTime base timestamp
     * @param secondsOffset seconds to add (positive) or subtract (negative)
     * @param price the price value
     * @return PriceRecord
     */
    public static PriceRecord createPriceWithOffset(String id, Instant baseTime, long secondsOffset, double price) {
        return new PriceRecord(id, baseTime.plusSeconds(secondsOffset), price);
    }
}
