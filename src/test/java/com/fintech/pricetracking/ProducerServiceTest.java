package com.fintech.pricetracking;

import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.exception.InvalidBatchOperationException;
import com.fintech.pricetracking.exception.InvalidChunkSizeException;
import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import com.fintech.pricetracking.service.ConsumerService;
import com.fintech.pricetracking.service.PriceTrackingConsumerService;
import com.fintech.pricetracking.service.ProducerService;
import com.fintech.pricetracking.service.PriceTrackingProducerService;
import com.fintech.pricetracking.strategy.LatestAsOfPriceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for ProducerService implementation.
 * Tests batch lifecycle, chunk uploads, validation, and error handling.
 */
class ProducerServiceTest {

    private ProducerService producer;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        producer = new PriceTrackingProducerService(
            new InMemoryBatchManager(),
            new InMemoryPriceRepository(),
            new LatestAsOfPriceSelectionStrategy()
        );
        baseTime = Instant.now();
    }

    @Test
    @DisplayName("Should return a unique non-null batch ID when starting a new batch")
    void testStartBatchReturnsUniqueBatchId() {
        String batchId = producer.startBatch();
        
        assertThat(batchId).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Should return different batch IDs for multiple batch starts")
    void testStartBatchReturnsUniqueBatchIds() {
        String batchId1 = producer.startBatch();
        String batchId2 = producer.startBatch();
        
        assertThat(batchId1).isNotEqualTo(batchId2);
    }

    @Test
    @DisplayName("Should successfully upload chunk to a valid batch")
    void testUploadChunkWithValidBatch() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> records = List.of(
            TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPriceWithOffset("GOOGL", baseTime, 60, 2800.0)
        );
        
        assertThatCode(() -> producer.uploadChunk(batchId, records))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw InvalidBatchOperationException when uploading to non-existent batch")
    void testUploadChunkWithInvalidBatchIdThrowsException() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)
        );
        
        assertThatThrownBy(() -> producer.uploadChunk("invalid-batch-id", records))
            .isInstanceOf(InvalidBatchOperationException.class)
            .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when uploading null records")
    void testUploadChunkWithNullRecordsThrowsException() {
        String batchId = producer.startBatch();
        
        assertThatThrownBy(() -> producer.uploadChunk(batchId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("records must not be null");
    }

    @Test
    @DisplayName("Should successfully upload chunk with exactly 1000 records (max allowed)")
    void testUploadChunkWith1000RecordsSucceeds() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> records = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            records.add(new PriceRecord("INST_" + i, baseTime, 100.0 + i));
        }
        
        assertThatCode(() -> producer.uploadChunk(batchId, records))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw InvalidChunkSizeException when uploading more than 1000 records")
    void testUploadChunkWith1001RecordsThrowsException() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> records = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            records.add(new PriceRecord("INST_" + i, baseTime, 100.0 + i));
        }
        
        assertThatThrownBy(() -> producer.uploadChunk(batchId, records))
            .isInstanceOf(InvalidChunkSizeException.class)
            .hasMessageContaining("exceeds maximum of 1000");
    }

    @Test
    @DisplayName("Should successfully upload multiple chunks to the same batch")
    void testMultipleChunksInSingleBatch() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> chunk1 = List.of(
            TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)
        );
        producer.uploadChunk(batchId, chunk1);
        
        List<PriceRecord> chunk2 = List.of(
            TestDataFactory.createPriceWithOffset("GOOGL", baseTime, 60, 2800.0)
        );
        producer.uploadChunk(batchId, chunk2);
        
        List<PriceRecord> chunk3 = List.of(
            TestDataFactory.createPriceWithOffset("MSFT", baseTime, 60, 350.0)
        );
        producer.uploadChunk(batchId, chunk3);
        
        assertThatCode(() -> producer.completeBatch(batchId))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should successfully complete a valid batch with uploaded records")
    void testCompleteBatchWithValidBatch() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> records = List.of(
            TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)
        );
        producer.uploadChunk(batchId, records);
        
        assertThatCode(() -> producer.completeBatch(batchId))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw InvalidBatchOperationException when completing non-existent batch")
    void testCompleteBatchWithInvalidBatchIdThrowsException() {
        assertThatThrownBy(() -> producer.completeBatch("invalid-batch-id"))
            .isInstanceOf(InvalidBatchOperationException.class)
            .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should successfully cancel a valid batch and discard all records")
    void testCancelBatchWithValidBatch() {
        String batchId = producer.startBatch();
        
        List<PriceRecord> records = List.of(
            TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)
        );
        producer.uploadChunk(batchId, records);
        
        assertThatCode(() -> producer.cancelBatch(batchId))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw InvalidBatchOperationException when canceling non-existent batch")
    void testCancelBatchWithInvalidBatchIdThrowsException() {
        assertThatThrownBy(() -> producer.cancelBatch("invalid-batch-id"))
            .isInstanceOf(InvalidBatchOperationException.class)
            .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should successfully process multiple sequential batches independently")
    void testMultipleSequentialBatches() {
        String batch1 = producer.startBatch();
        producer.uploadChunk(batch1, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)));
        producer.completeBatch(batch1);
        
        String batch2 = producer.startBatch();
        producer.uploadChunk(batch2, List.of(TestDataFactory.createPriceWithOffset("GOOGL", baseTime, 60, 2800.0)));
        producer.completeBatch(batch2);
        
        String batch3 = producer.startBatch();
        producer.uploadChunk(batch3, List.of(TestDataFactory.createPriceWithOffset("MSFT", baseTime, 60, 350.0)));
        producer.completeBatch(batch3);
        
        assertThat(batch1).isNotEqualTo(batch2).isNotEqualTo(batch3);
    }

    @Test
    @DisplayName("Should throw NullPointerException when constructing with null BatchManager")
    void testConstructorValidatesNullBatchManager() {
        assertThatThrownBy(() -> 
            new PriceTrackingProducerService(null, new InMemoryPriceRepository(), new LatestAsOfPriceSelectionStrategy())
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when constructing with null PriceRepository")
    void testConstructorValidatesNullPriceRepository() {
        assertThatThrownBy(() -> 
            new PriceTrackingProducerService(new InMemoryBatchManager(), null, new LatestAsOfPriceSelectionStrategy())
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when constructing with null PriceSelectionStrategy")
    void testConstructorValidatesNullPriceSelectionStrategy() {
        assertThatThrownBy(() -> 
            new PriceTrackingProducerService(new InMemoryBatchManager(), new InMemoryPriceRepository(), null)
        ).isInstanceOf(NullPointerException.class);
    }


    @Test
    @DisplayName("Should select latest asOf when same instrument appears in multiple chunks of same batch")
    void testSameBatchMultipleChunksSelectsLatestAsOf() {
        InMemoryPriceRepository sharedRepo = new InMemoryPriceRepository();
        ProducerService prod = new PriceTrackingProducerService(
            new InMemoryBatchManager(), sharedRepo, new LatestAsOfPriceSelectionStrategy()
        );
        ConsumerService cons = new PriceTrackingConsumerService(sharedRepo);
        
        String batchId = prod.startBatch();
        prod.uploadChunk(batchId, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)));
        prod.uploadChunk(batchId, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 300, 155.0)));
        prod.uploadChunk(batchId, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 180, 152.0)));
        prod.completeBatch(batchId);
        
        Optional<PriceRecord> price = cons.getLatestPrice("AAPL");
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.plusSeconds(300));
        assertThat(price.get().payload()).isEqualTo(155.0);
    }

    @Test
    @DisplayName("Should accept cross-batch update when new batch has newer asOf timestamp")
    void testCrossBatchUpdateWithNewerAsOf() {
        InMemoryPriceRepository sharedRepo = new InMemoryPriceRepository();
        ProducerService prod = new PriceTrackingProducerService(
            new InMemoryBatchManager(), sharedRepo, new LatestAsOfPriceSelectionStrategy()
        );
        ConsumerService cons = new PriceTrackingConsumerService(sharedRepo);
        
        String batch1 = prod.startBatch();
        prod.uploadChunk(batch1, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)));
        prod.completeBatch(batch1);
        
        String batch2 = prod.startBatch();
        prod.uploadChunk(batch2, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 600, 155.0)));
        prod.completeBatch(batch2);
        
        assertThat(cons.getLatestPrice("AAPL").get().payload()).isEqualTo(155.0);
    }

    @Test
    @DisplayName("Should reject cross-batch update when new batch has older asOf timestamp")
    void testCrossBatchUpdateWithOlderAsOfIsRejected() {
        InMemoryPriceRepository sharedRepo = new InMemoryPriceRepository();
        ProducerService prod = new PriceTrackingProducerService(
            new InMemoryBatchManager(), sharedRepo, new LatestAsOfPriceSelectionStrategy()
        );
        ConsumerService cons = new PriceTrackingConsumerService(sharedRepo);
        
        String batch1 = prod.startBatch();
        prod.uploadChunk(batch1, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 600, 155.0)));
        prod.completeBatch(batch1);
        
        String batch2 = prod.startBatch();
        prod.uploadChunk(batch2, List.of(TestDataFactory.createPriceWithOffset("AAPL", baseTime, 60, 150.0)));
        prod.completeBatch(batch2);
        
        Optional<PriceRecord> price = cons.getLatestPrice("AAPL");
        assertThat(price.get().asOf()).isEqualTo(baseTime.plusSeconds(600));
        assertThat(price.get().payload()).isEqualTo(155.0);
    }
}
