package com.fintech.pricetracking.service;

import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.PriceRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Consumer service implementation for retrieving price data.
 * 
 * <p>This class implements ONLY consumer operations, following the
 * Interface Segregation Principle (ISP). Consumers don't need to know
 * about producer operations.
 * 
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Retrieve latest price for a given instrument</li>
 *   <li>Provide read-only access to published prices</li>
 * </ul>
 *
 * 
 * <h2>Thread Safety:</h2>
 * <p>This class is thread-safe. Multiple consumers can call getLatestPrice()
 * concurrently without blocking each other (thanks to ReadWriteLock in repository).
 */
public  class PriceTrackingConsumerService implements ConsumerService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PriceTrackingConsumerService.class);
    
    private final PriceRepository priceRepository;

    /**
     * Constructs a new PriceTrackingConsumerService with injected repository.
     * 
     * @param priceRepository handles price data retrieval
     * @throws NullPointerException if priceRepository is null
     */
    public PriceTrackingConsumerService(PriceRepository priceRepository) {
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository cannot be null");
    }

    /**
     * Retrieves the latest price for a given instrument.
     * 
     * <p><b>REQUIREMENT:</b> "Consumers can request the last price record for a given id"
     * <p><b>REQUIREMENT:</b> "The last value is determined by the asOf time"
     * 
     * <p><b>How "latest by asOf" works:</b>
     * <ul>
     *   <li>Repository maintains full history sorted by asOf (newest first)</li>
     *   <li>This method returns the first element (latest) from history</li>
     *   <li>Fast: O(1) lookup, no comparison needed</li>
     * </ul>
     * 
     * @param instrumentId the instrument identifier to look up
     * @return Optional containing the latest PriceRecord, or empty if not found
     * @throws NullPointerException if instrumentId is null
     */
    @Override
    public Optional<PriceRecord> getLatestPrice(String instrumentId) {
        Objects.requireNonNull(instrumentId, "instrumentId cannot be null");
        logger.debug("Consumer requesting latest price for: {}", instrumentId);
        
        Optional<PriceRecord> result = priceRepository.findByInstrumentId(instrumentId);
        if (result.isPresent()) {
            logger.debug("Returning price for {}: ${}", instrumentId, result.get().payload());
        } else {
            logger.debug("No price found for: {}", instrumentId);
        }
        
        return result;
    }
}
