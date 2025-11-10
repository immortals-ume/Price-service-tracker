package com.fintech.pricetracking;

import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import com.fintech.pricetracking.service.ConsumerService;
import com.fintech.pricetracking.service.ProducerService;
import com.fintech.pricetracking.service.PriceTrackingServiceFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PriceTrackingServiceFactory singleton pattern.
 */
class PriceTrackingServiceFactoryTest {

    @Test
    void testGetProducerInstanceReturnsSingleton() {
        ProducerService instance1 = PriceTrackingServiceFactory.getProducerInstance();
        ProducerService instance2 = PriceTrackingServiceFactory.getProducerInstance();
        
        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testGetConsumerInstanceReturnsSingleton() {
        ConsumerService instance1 = PriceTrackingServiceFactory.getConsumerInstance();
        ConsumerService instance2 = PriceTrackingServiceFactory.getConsumerInstance();
        
        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testProducerAndConsumerShareSameRepository() {
        ProducerService producer = PriceTrackingServiceFactory.getProducerInstance();
        ConsumerService consumer = PriceTrackingServiceFactory.getConsumerInstance();
        
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            new PriceRecord("AAPL", Instant.now(), 150.0)
        ));
        producer.completeBatch(batchId);
        
        assertThat(consumer.getLatestPrice("AAPL")).isPresent();
    }

    @Test
    void testCreateCustomProducerInstance() {
        ProducerService customProducer = PriceTrackingServiceFactory.createCustomProducerInstance(
            new InMemoryBatchManager(),
            new InMemoryPriceRepository()
        );
        
        assertThat(customProducer).isNotNull();
        
        String batchId = customProducer.startBatch();
        assertThat(batchId).isNotNull();
    }

    @Test
    void testCreateCustomConsumerInstance() {
        ConsumerService customConsumer = PriceTrackingServiceFactory.createCustomConsumerInstance(
            new InMemoryPriceRepository()
        );
        
        assertThat(customConsumer).isNotNull();
        assertThat(customConsumer.getLatestPrice("AAPL")).isEmpty();
    }

    @Test
    void testCustomInstancesAreNotSingletons() {
        ProducerService custom1 = PriceTrackingServiceFactory.createCustomProducerInstance(
            new InMemoryBatchManager(),
            new InMemoryPriceRepository()
        );
        
        ProducerService custom2 = PriceTrackingServiceFactory.createCustomProducerInstance(
            new InMemoryBatchManager(),
            new InMemoryPriceRepository()
        );
        
        assertThat(custom1).isNotSameAs(custom2);
    }
}
