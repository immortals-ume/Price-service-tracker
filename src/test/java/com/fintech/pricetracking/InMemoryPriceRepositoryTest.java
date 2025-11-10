package com.fintech.pricetracking;

import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.repository.InMemoryPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for InMemoryPriceRepository covering all methods and edge cases.
 */
class InMemoryPriceRepositoryTest {

    private InMemoryPriceRepository repository;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPriceRepository();
        baseTime = Instant.now();
    }

    @Test
    @DisplayName("findByInstrumentId returns empty Optional for non-existent instrument")
    void testFindByInstrumentIdReturnsEmptyForNonExistent() {
        Optional<PriceRecord> price = repository.findByInstrumentId("NONEXISTENT");
        
        assertThat(price).isEmpty();
    }

    @Test
    @DisplayName("findByInstrumentId throws NullPointerException when instrumentId is null")
    void testFindByInstrumentIdValidatesNull() {
        assertThatThrownBy(() -> repository.findByInstrumentId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("instrumentId cannot be null");
    }

    @Test
    @DisplayName("saveAllRecords stores multiple prices successfully")
    void testSaveAllRecordsStoresPrices() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0)
        );
        
        repository.saveAllRecords(records);
        
        assertThat(repository.findByInstrumentId("AAPL")).isPresent();
        assertThat(repository.findByInstrumentId("GOOGL")).isPresent();
    }

    @Test
    @DisplayName("saveAllRecords throws NullPointerException when records list is null")
    void testSaveAllRecordsValidatesNull() {
        assertThatThrownBy(() -> repository.saveAllRecords(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("records list cannot be null");
    }

    @Test
    @DisplayName("saveAllRecords adds newer price to history and consumer gets latest")
    void testSaveAllRecordsWithNewerAsOfUpdatesLatest() {
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0)  // 2 min ago (older)
        ));
        
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 160.0)  // 1 min ago (newer)
        ));
        
        Optional<PriceRecord> price = repository.findByInstrumentId("AAPL");
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.minusSeconds(60));
        assertThat(price.get().payload()).isEqualTo(160.0);
    }

    @Test
    @DisplayName("saveAllRecords adds older price to history but consumer still gets latest")
    void testSaveAllRecordsWithOlderAsOfAddsToHistory() {
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 160.0)
        ));
        
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0)
        ));
        
        // Consumer still gets the latest price
        Optional<PriceRecord> price = repository.findByInstrumentId("AAPL");
        assertThat(price).isPresent();
        assertThat(price.get().asOf()).isEqualTo(baseTime.minusSeconds(60));
        assertThat(price.get().payload()).isEqualTo(160.0);
        
        // But history contains both
        var history = repository.findHistoryByInstrumentId("AAPL");
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("saveAllRecords stores new instrument successfully")
    void testSaveAllRecordsWithNewInstrumentStoresIt() {
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));
        
        assertThat(repository.findByInstrumentId("AAPL")).isPresent();
    }

    @Test
    @DisplayName("clear removes all stored prices")
    void testClearRemovesAllPrices() {
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0)
        ));
        
        repository.clear();
        
        assertThat(repository.findByInstrumentId("AAPL")).isEmpty();
        assertThat(repository.findByInstrumentId("GOOGL")).isEmpty();
    }

    @Test
    @DisplayName("size returns correct count of stored prices")
    void testSizeReturnsCorrectCount() {
        assertThat(repository.size()).isZero();
        
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0)
        ));
        
        assertThat(repository.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("contains returns true for existing instrument and false for non-existent")
    void testContainsReturnsTrueForExistingInstrument() {
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        ));
        
        assertThat(repository.contains("AAPL")).isTrue();
        assertThat(repository.contains("GOOGL")).isFalse();
    }

    @Test
    @DisplayName("findHistoryByInstrumentId returns empty list for non-existent instrument")
    void testFindHistoryReturnsEmptyForNonExistent() {
        var history = repository.findHistoryByInstrumentId("NONEXISTENT");
        
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("findHistoryByInstrumentId returns all prices sorted by asOf (newest first)")
    void testFindHistoryReturnsSortedPrices() {
        // Add prices in random order
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0)  // 2 min ago (oldest)
        ));
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 30, 170.0)   // 30 sec ago (LATEST)
        ));
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 160.0)   // 1 min ago
        ));
        
        var history = repository.findHistoryByInstrumentId("AAPL");
        
        assertThat(history).hasSize(3);
        assertThat(history.get(0).asOf()).isEqualTo(baseTime.minusSeconds(30));   // Latest first
        assertThat(history.get(0).payload()).isEqualTo(170.0);
        assertThat(history.get(1).asOf()).isEqualTo(baseTime.minusSeconds(60));
        assertThat(history.get(1).payload()).isEqualTo(160.0);
        assertThat(history.get(2).asOf()).isEqualTo(baseTime.minusSeconds(120));  // Oldest last
        assertThat(history.get(2).payload()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("findByInstrumentId returns latest price when history exists")
    void testFindByInstrumentIdReturnsLatestFromHistory() {
        repository.saveAllRecords(List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0),  // 2 min ago (oldest)
            TestDataFactory.createPrice("AAPL", baseTime, 60, 160.0),   // 1 min ago
            TestDataFactory.createPrice("AAPL", baseTime, 30, 170.0)    // 30 sec ago (LATEST)
        ));
        
        Optional<PriceRecord> latest = repository.findByInstrumentId("AAPL");
        
        assertThat(latest).isPresent();
        assertThat(latest.get().asOf()).isEqualTo(baseTime.minusSeconds(30));
        assertThat(latest.get().payload()).isEqualTo(170.0);
    }

    @Test
    @DisplayName("findHistoryByInstrumentId throws NullPointerException when instrumentId is null")
    void testFindHistoryValidatesNull() {
        assertThatThrownBy(() -> repository.findHistoryByInstrumentId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("instrumentId cannot be null");
    }
}
