package com.fintech.pricetracking.service;

import com.fintech.pricetracking.batch.BatchManager;
import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import com.fintech.pricetracking.repository.PriceRepository;
import com.fintech.pricetracking.strategy.LatestAsOfPriceSelectionStrategy;
import com.fintech.pricetracking.strategy.PriceSelectionStrategy;

/**
 * Factory class that provides singleton instances of Producer and Consumer services.
 * 
 * <p>Implements Singleton pattern using Bill Pugh Initialization-on-demand holder idiom.
 * 
 * <h2>Design Pattern: Singleton (Bill Pugh)</h2>
 * Thread-safe lazy initialization without synchronization overhead.
 * 
 * <h2>Separation of Concerns (ISP):</h2>
 * Producer and Consumer services are separate classes sharing the same repository.
 */
public class PriceTrackingServiceFactory {
    
    private PriceTrackingServiceFactory() {
        throw new AssertionError("Cannot instantiate PriceTrackingServiceFactory");
    }
    
    /**
     * Shared dependencies holder.
     */
    private static class SharedDependencies {
        private static final BatchManager BATCH_MANAGER = new InMemoryBatchManager();
        private static final PriceRepository PRICE_REPOSITORY = new InMemoryPriceRepository();
        private static final PriceSelectionStrategy PRICE_SELECTION_STRATEGY = new LatestAsOfPriceSelectionStrategy();
    }
    
    /**
     * Producer service singleton holder.
     */
    private static class ProducerInstanceHolder {
        private static final ProducerService INSTANCE = new PriceTrackingProducerService(
            SharedDependencies.BATCH_MANAGER,
            SharedDependencies.PRICE_REPOSITORY,
            SharedDependencies.PRICE_SELECTION_STRATEGY
        );
    }
    
    /**
     * Consumer service singleton holder.
     */
    private static class ConsumerInstanceHolder {
        private static final ConsumerService INSTANCE = new PriceTrackingConsumerService(
            SharedDependencies.PRICE_REPOSITORY
        );
    }
    
    /**
     * Returns the singleton ProducerService instance.
     */
    public static ProducerService getProducerInstance() {
        return ProducerInstanceHolder.INSTANCE;
    }
    
    /**
     * Returns the singleton ConsumerService instance.
     */
    public static ConsumerService getConsumerInstance() {
        return ConsumerInstanceHolder.INSTANCE;
    }
    
    /**
     * Creates custom ProducerService for testing.
     */
    public static ProducerService createCustomProducerInstance(
            BatchManager batchManager,
            PriceRepository priceRepository,
            PriceSelectionStrategy priceSelectionStrategy) {
        return new PriceTrackingProducerService(batchManager, priceRepository, priceSelectionStrategy);
    }
    
    /**
     * Creates custom ConsumerService for testing.
     */
    public static ConsumerService createCustomConsumerInstance(PriceRepository priceRepository) {
        return new PriceTrackingConsumerService(priceRepository);
    }
}
