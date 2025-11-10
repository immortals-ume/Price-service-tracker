package com.fintech.pricetracking.model;

import java.time.Instant;

/**
 * Immutable data class representing a single price record for a financial instrument.
 * Contains an instrument identifier, timestamp, and flexible payload.
 */
public record PriceRecord(String id, Instant asOf, Object payload) {
    /**
     * Constructs a new PriceRecord.
     *
     * @param id      the instrument identifier (must not be null)
     * @param asOf    the timestamp when the price was determined (must not be null)
     * @param payload the price data (must not be null)
     * @throws IllegalArgumentException if any parameter is null
     */
    public PriceRecord {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (asOf == null) {
            throw new IllegalArgumentException("asOf must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

    }

    @Override
    public String toString() {
        return "PriceRecord{" +
                "id='" + id + '\'' +
                ", asOf=" + asOf +
                ", payload=" + payload +
                '}';
    }
}
