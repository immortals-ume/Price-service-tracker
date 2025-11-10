package com.fintech.pricetracking;

import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.strategy.LatestAsOfPriceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for LatestAsOfPriceSelectionStrategy covering all selection logic.
 */
class LatestAsOfPriceSelectionStrategyTest {

    private LatestAsOfPriceSelectionStrategy strategy;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        strategy = new LatestAsOfPriceSelectionStrategy();
        baseTime = Instant.now();
    }

    @Test
    void testSelectPricesWithEmptyList() {
        Map<String, PriceRecord> result = strategy.selectPrices(List.of());
        
        assertThat(result).isEmpty();
    }

    @Test
    void testSelectPricesWithSingleRecord() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0)
        );
        
        Map<String, PriceRecord> result = strategy.selectPrices(records);
        
        assertThat(result).hasSize(1);
        assertThat(result.get("AAPL").payload()).isEqualTo(150.0);
    }

    @Test
    void testSelectPricesWithMultipleDifferentInstruments() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0),
            TestDataFactory.createPrice("MSFT", baseTime, 60, 350.0)
        );
        
        Map<String, PriceRecord> result = strategy.selectPrices(records);
        
        assertThat(result).hasSize(3);
        assertThat(result.get("AAPL").payload()).isEqualTo(150.0);
        assertThat(result.get("GOOGL").payload()).isEqualTo(2800.0);
        assertThat(result.get("MSFT").payload()).isEqualTo(350.0);
    }

    @Test
    void testSelectPricesWithSameInstrumentSelectsLatestAsOf() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 120, 150.0),  // 2 min ago (oldest)
            TestDataFactory.createPrice("AAPL", baseTime, 30, 155.0),   // 30 sec ago (LATEST)
            TestDataFactory.createPrice("AAPL", baseTime, 60, 152.0)    // 1 min ago
        );
        
        Map<String, PriceRecord> result = strategy.selectPrices(records);
        
        assertThat(result).hasSize(1);
        assertThat(result.get("AAPL").asOf()).isEqualTo(baseTime.minusSeconds(30));
        assertThat(result.get("AAPL").payload()).isEqualTo(155.0);
    }

    @Test
    void testSelectPricesWithSameAsOfTimestamp() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("AAPL", baseTime, 60, 151.0),
            TestDataFactory.createPrice("AAPL", baseTime, 60, 152.0)
        );
        
        Map<String, PriceRecord> result = strategy.selectPrices(records);
        
        assertThat(result).hasSize(1);
        double payload = (double) result.get("AAPL").payload();
        assertThat(payload).isIn(150.0, 151.0, 152.0);
    }

    @Test
    void testSelectPricesWithMultipleInstrumentsAndDuplicates() {
        List<PriceRecord> records = List.of(
            TestDataFactory.createPrice("AAPL", baseTime, 60, 150.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 60, 2800.0),
            TestDataFactory.createPrice("AAPL", baseTime, 10, 155.0),
            TestDataFactory.createPrice("GOOGL", baseTime, 5, 2850.0),
            TestDataFactory.createPrice("MSFT", baseTime, 60, 350.0)
        );
        
        Map<String, PriceRecord> result = strategy.selectPrices(records);
        
        assertThat(result).hasSize(3);
    }
}
