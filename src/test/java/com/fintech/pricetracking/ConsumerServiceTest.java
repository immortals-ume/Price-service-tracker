package com.fintech.pricetracking;

import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import com.fintech.pricetracking.repository.PriceRepository;
import com.fintech.pricetracking.service.ConsumerService;
import com.fintech.pricetracking.service.ProducerService;
import com.fintech.pricetracking.service.PriceTrackingConsumerService;
import com.fintech.pricetracking.service.PriceTrackingProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ConsumerService - refactored to use TestDataFactory with past timestamps.
 */
class ConsumerServiceTest {

    private ProducerService producer;
    private ConsumerService consumer;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        PriceRepository priceRepository = new InMemoryPriceRepository();
        producer = new PriceTrackingProducerService(
            new InMemoryBatchManager(),
            priceRepository
        );
        consumer = new PriceTrackingConsumerService(priceRepository);
        baseTime = Instant.now();
    }

    @Test
    @DisplayName("Should return empty Optional when requesting price for non-existent instrument")
    void testGetLatestPriceReturnsEmptyForNonExistentInstrument() {
        Optional price = consumer.getLatestPrice("NONEXISTENT");
        assertThat(price).isEmpty();
    }

    @Test
    @DisplayName("Should return price after batch is completed")
    void testGetLatestPriceAfterBatchCompletion() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));
        producer.completeBatch(batchId);
        
        var price = consumer.getLatestPrice("AAPL");
        
        assertThat(price).isPresent();
        assertThat(price.get().id()).isEqualTo("AAPL");
        assertThat(price.get().payload()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Should return empty Optional while batch is being uploaded")
    void testGetLatestPriceReturnsEmptyDuringBatchUpload() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));
        
        Optional price = consumer.getLatestPrice("AAPL");
        assertThat(price).isEmpty();
    }

    @Test
    void testGetLatestPriceReturnsEmptyAfterBatchCancellation() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));
        producer.cancelBatch(batchId);
        
        Optional price = consumer.getLatestPrice("AAPL");
        assertThat(price).isEmpty();
    }

    @Test
    void testGetLatestPriceSelectsMostRecentAsOfWithinBatch() {
        String batchId = producer.startBatch();
        var prices = TestDataFactory.createMultiplePricesForSameInstrument("AAPL", baseTime);
        producer.uploadChunk(batchId, prices);
        producer.completeBatch(batchId);
        
        var price = consumer.getLatestPrice("AAPL");
        
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.minusSeconds(30));
        assertThat(price.get().payload()).isEqualTo(155.0);
    }

    @Test
    void testGetLatestPriceSelectsMostRecentAsOfAcrossChunks() {
        String batchId = producer.startBatch();
        
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0)
        ));
        
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 30, 160.0)  // Latest
        ));
        
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 155.0)
        ));
        
        producer.completeBatch(batchId);
        
        var price = consumer.getLatestPrice("AAPL");
        
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.minusSeconds(30));
        assertThat(price.get().payload()).isEqualTo(160.0);
    }

    @Test
    void testGetLatestPriceWithCrossBatchNewerAsOf() {
        String batch1 = producer.startBatch();
        producer.uploadChunk(batch1, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0)
        ));
        producer.completeBatch(batch1);
        
        String batch2 = producer.startBatch();
        producer.uploadChunk(batch2, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 160.0)  // Newer
        ));
        producer.completeBatch(batch2);
        
        var price = consumer.getLatestPrice("AAPL");
        
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.minusSeconds(60));
        assertThat(price.get().payload()).isEqualTo(160.0);
    }

    @Test
    void testGetLatestPriceWithCrossBatchOlderAsOfKeepsLatest() {
        String batch1 = producer.startBatch();
        producer.uploadChunk(batch1, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 160.0)  // Newer
        ));
        producer.completeBatch(batch1);
        
        String batch2 = producer.startBatch();
        producer.uploadChunk(batch2, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0)  // Older - added to history
        ));
        producer.completeBatch(batch2);
        
        var price = consumer.getLatestPrice("AAPL");
        
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.minusSeconds(60));
        assertThat(price.get().payload()).isEqualTo(160.0);
    }

    @Test
    void testGetLatestPriceWithMultipleInstruments() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0),
            TestDataFactory.createPrice("MSFT", baseTime, 60, 350.0)
        ));
        producer.completeBatch(batchId);
        
        assertThat(consumer.getLatestPrice("AAPL")).isPresent();
        assertThat(consumer.getLatestPrice("GOOGL")).isPresent();
        assertThat(consumer.getLatestPrice("MSFT")).isPresent();
        assertThat(consumer.getLatestPrice("TSLA")).isEmpty();
    }

    @Test
    void testGetLatestPriceWithNullInstrumentIdThrowsException() {
        assertThatThrownBy(() -> consumer.getLatestPrice(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorValidatesNullPriceRepository() {
        assertThatThrownBy(() -> new PriceTrackingConsumerService(null))
            .isInstanceOf(NullPointerException.class);
    }
}
