package com.fintech.pricetracking;

import com.fintech.pricetracking.batch.InMemoryBatchManager;
import com.fintech.pricetracking.model.PriceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for InMemoryBatchManager covering all methods and edge cases.
 */
class InMemoryBatchManagerTest {

    private InMemoryBatchManager batchManager;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        batchManager = new InMemoryBatchManager();
        baseTime = Instant.now();
    }


    @Test
    @DisplayName("Should return a unique batch ID when creating a batch")
    void testCreateBatchReturnsUniqueBatchId() {
        String batchId = batchManager.createBatch();

        assertThat(batchId).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Should return different batch IDs for multiple batch creations")
    void testCreateBatchReturnsUniqueBatchIds() {
        String batchId1 = batchManager.createBatch();
        String batchId2 = batchManager.createBatch();

        assertThat(batchId1).isNotEqualTo(batchId2);
    }

    @Test
    @DisplayName("Should return true when checking if a created batch exists")
    void testBatchExistsReturnsTrueForCreatedBatch() {
        String batchId = batchManager.createBatch();

        assertThat(batchManager.batchExists(batchId)).isTrue();
    }

    @Test
    @DisplayName("Should return false when checking if a non-existent batch exists")
    void testBatchExistsReturnsFalseForNonExistentBatch() {
        assertThat(batchManager.batchExists("non-existent")).isFalse();
    }

    @Test
    @DisplayName("Should successfully add records to an existing batch")
    void testAddRecordsToExistingBatch() {
        String batchId = batchManager.createBatch();
        List<PriceRecord> records = List.of(
                TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        );

        assertThatCode(() -> batchManager.addRecords(batchId, records))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when adding records to a non-existent batch")
    void testAddRecordsToNonExistentBatchThrowsException() {
        List<PriceRecord> records = List.of(
                TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        );

        assertThatThrownBy(() -> batchManager.addRecords("non-existent", records))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should return all added records for a batch")
    void testGetBatchRecordsReturnsAddedRecords() {
        String batchId = batchManager.createBatch();
        List<PriceRecord> records = List.of(
                TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
                TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0)
        );
        batchManager.addRecords(batchId, records);

        List<PriceRecord> retrieved = batchManager.getBatchRecords(batchId);

        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(0).id()).isEqualTo("AAPL");
        assertThat(retrieved.get(1).id()).isEqualTo("GOOGL");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when getting records from a non-existent batch")
    void testGetBatchRecordsFromNonExistentBatchThrowsException() {
        assertThatThrownBy(() -> batchManager.getBatchRecords("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should return an unmodifiable list when getting batch records")
    void testGetBatchRecordsReturnsUnmodifiableList() {
        String batchId = batchManager.createBatch();
        batchManager.addRecords(batchId, List.of(
                TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));

        List<PriceRecord> records = batchManager.getBatchRecords(batchId);

        assertThatThrownBy(() -> records.add(TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should successfully remove an existing batch")
    void testRemoveBatchRemovesBatch() {
        String batchId = batchManager.createBatch();

        batchManager.removeBatch(batchId);

        assertThat(batchManager.batchExists(batchId)).isFalse();
    }

    @Test
    @DisplayName("Should not throw exception when removing a non-existent batch")
    void testRemoveBatchWithNonExistentBatchDoesNotThrow() {
        assertThatCode(() -> batchManager.removeBatch("non-existent"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accumulate records when adding multiple times to the same batch")
    void testMultipleAddRecordsAccumulatesRecords() {
        String batchId = batchManager.createBatch();

        batchManager.addRecords(batchId, List.of(
                TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));

        batchManager.addRecords(batchId, List.of(
                TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0)
        ));

        List<PriceRecord> records = batchManager.getBatchRecords(batchId);
        assertThat(records).hasSize(2);
    }
}
