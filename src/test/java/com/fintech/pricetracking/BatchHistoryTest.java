package com.fintech.pricetracking;

import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.model.BatchAudit;
import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import com.fintech.pricetracking.service.ProducerService;
import com.fintech.pricetracking.service.PriceTrackingProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for batch history tracking.
 */
class BatchHistoryTest {

    private InMemoryBatchManager batchManager;
    private ProducerService producer;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        batchManager = new InMemoryBatchManager();
        producer = new PriceTrackingProducerService(
            batchManager,
            new InMemoryPriceRepository()
        );
        baseTime = Instant.now();
    }

    @Test
    @DisplayName("Should record completed batch in history")
    void testCompletedBatchRecordedInHistory() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            new PriceRecord("AAPL", baseTime, 150.0)
        ));
        producer.completeBatch(batchId);
        
        List<BatchAudit> history = batchManager.getBatchHistory();
        
        assertThat(history).hasSize(1);
        assertThat(history.get(0).batchId()).isEqualTo(batchId);
        assertThat(history.get(0).status()).isEqualTo(BatchAudit.BatchStatus.COMPLETED);
        assertThat(history.get(0).recordCount()).isEqualTo(1);
        assertThat(history.get(0).completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should record cancelled batch in history")
    void testCancelledBatchRecordedInHistory() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(
            new PriceRecord("AAPL", baseTime, 150.0),
            new PriceRecord("GOOGL", baseTime, 2800.0)
        ));
        producer.cancelBatch(batchId);
        
        List<BatchAudit> history = batchManager.getBatchHistory();
        
        assertThat(history).hasSize(1);
        assertThat(history.get(0).batchId()).isEqualTo(batchId);
        assertThat(history.get(0).status()).isEqualTo(BatchAudit.BatchStatus.CANCELLED);
        assertThat(history.get(0).recordCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should track multiple batches in history")
    void testMultipleBatchesInHistory() {
        String batch1 = producer.startBatch();
        producer.uploadChunk(batch1, List.of(new PriceRecord("AAPL", baseTime, 150.0)));
        producer.completeBatch(batch1);
        
        String batch2 = producer.startBatch();
        producer.uploadChunk(batch2, List.of(new PriceRecord("GOOGL", baseTime, 2800.0)));
        producer.cancelBatch(batch2);
        
        String batch3 = producer.startBatch();
        producer.uploadChunk(batch3, List.of(new PriceRecord("MSFT", baseTime, 350.0)));
        producer.completeBatch(batch3);
        
        List<BatchAudit> history = batchManager.getBatchHistory();
        
        assertThat(history).hasSize(3);
        assertThat(history.get(0).status()).isEqualTo(BatchAudit.BatchStatus.COMPLETED);
        assertThat(history.get(1).status()).isEqualTo(BatchAudit.BatchStatus.CANCELLED);
        assertThat(history.get(2).status()).isEqualTo(BatchAudit.BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should return empty history when no batches completed")
    void testEmptyHistory() {
        List<BatchAudit> history = batchManager.getBatchHistory();
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("Should return unmodifiable history list")
    void testHistoryIsUnmodifiable() {
        String batchId = producer.startBatch();
        producer.uploadChunk(batchId, List.of(new PriceRecord("AAPL", baseTime, 150.0)));
        producer.completeBatch(batchId);
        
        List<BatchAudit> history = batchManager.getBatchHistory();
        
        assertThatThrownBy(() -> history.add(
            new BatchAudit("fake", Instant.now(), 0, BatchAudit.BatchStatus.COMPLETED)
        )).isInstanceOf(UnsupportedOperationException.class);
    }
}
