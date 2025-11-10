package com.fintech.pricetracking;

import com.fintech.pricetracking.model.PriceRecord;
import com.fintech.pricetracking.service.ConsumerService;
import com.fintech.pricetracking.service.ProducerService;
import com.fintech.pricetracking.service.PriceTrackingServiceFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple demo showing how to use the Price Tracking Service.
 * Uses PAST timestamps (realistic - prices determined in the past).
 */
public final class PriceTrackingDemo {
    
    public static void main(String[] args) {
        ProducerService producer = PriceTrackingServiceFactory.getProducerInstance();
        ConsumerService consumer = PriceTrackingServiceFactory.getConsumerInstance();
        
        System.out.println("=== Price Tracking Service Demo ===\n");

        System.out.println("1. Basic Upload");
        String batch1 = producer.startBatch();
        Instant now = Instant.now();
        producer.uploadChunk(batch1, List.of(
            new PriceRecord("AAPL", now.minusSeconds(60), 150.50),
            new PriceRecord("GOOGL", now.minusSeconds(30), 2800.75)
        ));
        producer.completeBatch(batch1);
        System.out.println("   ✓ Uploaded 2 prices");
        
        consumer.getLatestPrice("AAPL").ifPresent(price -> 
            System.out.println("   ✓ AAPL: $" + price.payload()));

        System.out.println("\n2. Multiple Chunks (1000 per chunk)");
        String batch2 = producer.startBatch();
        Instant baseTime = Instant.now();

        List<PriceRecord> chunk1 = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            chunk1.add(new PriceRecord("INST_" + i, baseTime.minusSeconds(1000 - i), 100.0 + i));
        }
        producer.uploadChunk(batch2, chunk1);
        System.out.println("   ✓ Chunk 1: 1000 records (timestamps: now-1000s to now-1s)");

        List<PriceRecord> chunk2 = new ArrayList<>();
        for (int i = 1000; i < 1500; i++) {
            chunk2.add(new PriceRecord("INST_" + i, baseTime.minusSeconds(1500 - i), 100.0 + i));
        }
        producer.uploadChunk(batch2, chunk2);
        System.out.println("   ✓ Chunk 2: 500 records (timestamps: now-500s to now-1s)");
        
        producer.completeBatch(batch2);
        System.out.println("   ✓ Batch completed: 1500 total");
        
        consumer.getLatestPrice("INST_0").ifPresent(price -> 
            System.out.println("   ✓ INST_0: $" + price.payload()));
        consumer.getLatestPrice("INST_1499").ifPresent(price -> 
            System.out.println("   ✓ INST_1499: $" + price.payload()));

        System.out.println("\n3. Latest Price Selection (same instrument, different past times)");
        String batch3 = producer.startBatch();
        Instant currentTime = Instant.now();
        producer.uploadChunk(batch3, List.of(
            new PriceRecord("TSLA", currentTime.minusSeconds(120), 250.0),  // 2 min ago
            new PriceRecord("TSLA", currentTime.minusSeconds(30), 255.0),   // 30 sec ago - LATEST
            new PriceRecord("TSLA", currentTime.minusSeconds(60), 252.0)    // 1 min ago
        ));
        producer.completeBatch(batch3);
        
        consumer.getLatestPrice("TSLA").ifPresent(price -> 
            System.out.println("   ✓ TSLA latest: $" + price.payload() + " (30 sec ago - most recent)"));
        
        System.out.println("\n✅ Done!");
    }
}
