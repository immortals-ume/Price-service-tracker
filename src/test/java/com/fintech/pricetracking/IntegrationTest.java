package com.fintech.pricetracking;

import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import com.fintech.pricetracking.repository.PriceRepository;
import com.fintech.pricetracking.service.ConsumerService;
import com.fintech.pricetracking.service.ProducerService;
import com.fintech.pricetracking.service.PriceTrackingServiceFactory;
import com.fintech.pricetracking.strategy.LatestAsOfPriceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests covering end-to-end scenarios with Producer and Consumer services.
 * Uses isolated instances per test to avoid shared state.
 */
class IntegrationTest {

    private ProducerService producer;
    private ConsumerService consumer;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        PriceRepository sharedRepository = new InMemoryPriceRepository();
        producer = PriceTrackingServiceFactory.createCustomProducerInstance(
            new InMemoryBatchManager(),
            sharedRepository,
            new LatestAsOfPriceSelectionStrategy()
        );
        consumer = PriceTrackingServiceFactory.createCustomConsumerInstance(sharedRepository);
        baseTime = Instant.parse("2024-01-01T10:00:00Z");
    }

    @Test
    void testCompleteBatchLifecycleWithAtomicity() {
        String batchId = producer.startBatch();
        
        producer.uploadChunk(batchId, List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0),
            TestDataFactory.createPrice("MSFT", baseTime, 60, 350.0)
        ));
        
        assertThat(consumer.getLatestPrice("AAPL")).isEmpty();
        assertThat(consumer.getLatestPrice("GOOGL")).isEmpty();
        assertThat(consumer.getLatestPrice("MSFT")).isEmpty();
        
        producer.completeBatch(batchId);
        
        assertThat(consumer.getLatestPrice("AAPL")).isPresent();
        assertThat(consumer.getLatestPrice("GOOGL")).isPresent();
        assertThat(consumer.getLatestPrice("MSFT")).isPresent();
    }

    @Test
    void testMultipleChunksUpTo1000Records() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> chunk1 = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            chunk1.add(new PriceRecord("INST_" + i, baseTime, 100.0 + i));
        }
        producer.uploadChunk(batchId, chunk1);
        
        List<PriceRecord> chunk2 = new ArrayList<>();
        for (int i = 1000; i < 2000; i++) {
            chunk2.add(new PriceRecord("INST_" + i, baseTime, 100.0 + i));
        }
        producer.uploadChunk(batchId, chunk2);
        
        List<PriceRecord> chunk3 = new ArrayList<>();
        for (int i = 2000; i < 2500; i++) {
            chunk3.add(new PriceRecord("INST_" + i, baseTime, 100.0 + i));
        }
        producer.uploadChunk(batchId, chunk3);
        
        producer.completeBatch(batchId);
        
        assertThat(consumer.getLatestPrice("INST_0")).isPresent();
        assertThat(consumer.getLatestPrice("INST_1500")).isPresent();
        assertThat(consumer.getLatestPrice("INST_2499")).isPresent();
    }

    @Test
    void testMultipleProducersWithSeparateBatches() throws InterruptedException {
        int numProducers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numProducers);
        CountDownLatch latch = new CountDownLatch(numProducers);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numProducers; i++) {
            final int producerId = i;
            executor.submit(() -> {
                try {
                    String batchId = producer.startBatch();
                    producer.uploadChunk(batchId, List.of(
                        new PriceRecord("INST_" + producerId, baseTime.plusSeconds(producerId), 100.0 + producerId)
                    ));
                    producer.completeBatch(batchId);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(numProducers);

        for (int i = 0; i < numProducers; i++) {
            Optional<PriceRecord> price = consumer.getLatestPrice("INST_" + i);
            assertThat(price).isPresent();
            assertThat(price.get().payload()).isEqualTo(100.0 + i);
        }
    }

    @Test
    void testConsumerReadingWhileProducerUploadsBatch() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch consumerCompleted = new CountDownLatch(1);

        executor.submit(() -> {
            String batchId = producer.startBatch();
            producer.uploadChunk(batchId, List.of(
                TestDataFactory.createPrice("CONCURRENT", baseTime, 60, 500.0)
            ));
            producerStarted.countDown();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            producer.completeBatch(batchId);
        });

        executor.submit(() -> {
            try {
                producerStarted.await(1, TimeUnit.SECONDS);
                Optional<PriceRecord> price = consumer.getLatestPrice("CONCURRENT");
                assertThat(price).isEmpty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                consumerCompleted.countDown();
            }
        });

        consumerCompleted.await(2, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);

        Optional<PriceRecord> finalPrice = consumer.getLatestPrice("CONCURRENT");
        assertThat(finalPrice).isPresent();
        assertThat(finalPrice.get().payload()).isEqualTo(500.0);
    }

    @Test
    void testComplexMultiBatchScenario() {
        Instant t1 = baseTime;
        Instant t2 = baseTime.plusSeconds(300);
        Instant t3 = baseTime.plusSeconds(600);
        
        String batch1 = producer.startBatch();
        producer.uploadChunk(batch1, List.of(
            new PriceRecord("AAPL", t1, 150.0),
            new PriceRecord("GOOGL", t1, 2800.0)
        ));
        producer.completeBatch(batch1);
        
        String batch2 = producer.startBatch();
        producer.uploadChunk(batch2, List.of(
            new PriceRecord("AAPL", t2, 152.0),
            new PriceRecord("MSFT", t2, 350.0)
        ));
        producer.completeBatch(batch2);
        
        String batch3 = producer.startBatch();
        producer.uploadChunk(batch3, List.of(
            new PriceRecord("AAPL", t3, 155.0),
            new PriceRecord("GOOGL", t3, 2850.0)
        ));
        producer.completeBatch(batch3);
        
        Optional<PriceRecord> aaplPrice = consumer.getLatestPrice("AAPL");
        assertThat(aaplPrice).isPresent();
        assertThat(aaplPrice.get().payload()).isEqualTo(155.0);
        assertThat(aaplPrice.get().asOf()).isEqualTo(t3);
        
        Optional<PriceRecord> googlPrice = consumer.getLatestPrice("GOOGL");
        assertThat(googlPrice).isPresent();
        assertThat(googlPrice.get().payload()).isEqualTo(2850.0);
        assertThat(googlPrice.get().asOf()).isEqualTo(t3);
        
        Optional<PriceRecord> msftPrice = consumer.getLatestPrice("MSFT");
        assertThat(msftPrice).isPresent();
        assertThat(msftPrice.get().payload()).isEqualTo(350.0);
        assertThat(msftPrice.get().asOf()).isEqualTo(t2);
    }

    @Test
    void testCrossBatchAsOfComparisonScenario() {
        Instant time1 = baseTime.plusSeconds(60);
        Instant time2 = baseTime;
        Instant time3 = baseTime.plusSeconds(120);
        
        String batch1 = producer.startBatch();
        producer.uploadChunk(batch1, List.of(new PriceRecord("TSLA", time1, 200.0)));
        producer.completeBatch(batch1);
        
        assertThat(consumer.getLatestPrice("TSLA").get().payload()).isEqualTo(200.0);
        
        String batch2 = producer.startBatch();
        producer.uploadChunk(batch2, List.of(new PriceRecord("TSLA", time2, 195.0)));
        producer.completeBatch(batch2);
        
        assertThat(consumer.getLatestPrice("TSLA").get().payload()).isEqualTo(200.0);
        
        String batch3 = producer.startBatch();
        producer.uploadChunk(batch3, List.of(new PriceRecord("TSLA", time3, 205.0)));
        producer.completeBatch(batch3);
        
        assertThat(consumer.getLatestPrice("TSLA").get().payload()).isEqualTo(205.0);
    }
}
