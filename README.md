# Price Tracking Service

In-memory price tracking service for financial instruments. Producers upload prices in batches, consumers retrieve latest prices.

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+

### Build
```bash
  mvn clean install
```

### Run Tests
```bash
  mvn test
```

### Run Demo
```bash
  mvn exec:java -Dexec.mainClass="com.fintech.pricetracking.PriceTrackingDemo"
```

## API

### ProducerService
- `startBatch()` - Create batch, returns ID
- `uploadChunk(batchId, records)` - Upload max 1000 records
- `completeBatch(batchId)` - Publish all prices atomically
- `cancelBatch(batchId)` - Discard batch

### ConsumerService
- `getLatestPrice(instrumentId)` - Get latest price by asOf time

### PriceRecord
- `id` - Instrument identifier (String)
- `asOf` - Price timestamp (Instant)
- `payload` - Price data (Object - flexible)

## Design Decisions

### 1. History-Based Storage
**Decision:** Store ALL prices per instrument, return latest

**Why:** 
- Enables auditing and compliance
- Supports historical analysis
- Consumer API unchanged (still gets latest)
- Repository: `Map<InstrumentId, List<PriceRecord>>` sorted by asOf (newest first)

### 2. Two-Stage Price Selection
**Decision:** Select latest price in two stages

**Stage 1 - Within Batch (Strategy Pattern):**
- Multiple prices for same instrument in one batch
- `LatestAsOfPriceSelectionStrategy` selects latest by asOf
- Only one price per instrument sent to repository

**Stage 2 - Across Batches (Repository):**
- Repository maintains sorted history
- New prices added to history list
- Consumer always gets first element (latest)

**Why:** Separation of concerns - strategy handles batch logic, repository handles storage

### 3. Past Timestamps
**Decision:** Use past timestamps (`.minusSeconds()`) in tests and demo

**Why:**
- Realistic - prices determined in the past, not future
- Demo: `now.minusSeconds(30)` = 30 seconds ago
- Tests: Use `TestDataFactory` for consistent past timestamps

### 4. Atomic Batch Completion
**Decision:** All prices in batch visible simultaneously

**Implementation:**
- `saveAll()` uses write lock
- Consumers blocked during write
- See all prices or none

**Why:** Business requirement - "all prices should be made available at the same time"

### 5. Thread Safety (ReadWriteLock)
**Decision:** Use ReadWriteLock instead of synchronized

**Why:**
- Multiple concurrent reads (no blocking)
- Exclusive writes (atomic updates)
- Better performance for read-heavy workloads

### 6. Separated Producer/Consumer Services
**Decision:** Two separate service interfaces

**Why:**
- Interface Segregation Principle (ISP)
- Producers don't need consumer methods
- Consumers don't need producer methods
- Clear separation of concerns

### 7. TestDataFactory Utility
**Decision:** Centralize test data creation

**Why:**
- DRY principle - no duplication
- Consistent past timestamps across tests
- Easy to maintain and update
- Single source of truth for test data

## Architecture

```
com.fintech.pricetracking
├── model/          # PriceRecord
├── service/        # Producer/Consumer services
├── repository/     # Price storage with history
├── batch/          # Batch management
├── strategy/       # Price selection (latest by asOf)
└── exception/      # Custom exceptions
```

## Key Behaviors

**Within Batch:** Strategy selects latest by asOf  
**Across Batches:** Repository maintains sorted history, returns latest  
**Example:**
```
Batch 1: AAPL @ 10:05 = $155 → stored
Batch 2: AAPL @ 10:00 = $150 → added to history (older)
Batch 3: AAPL @ 10:10 = $160 → stored as latest
Consumer gets: $160 (latest)
History: [$160, $155, $150] (sorted newest first)
```

## Testing

**74 tests - 100% passing**

- ProducerServiceTest (19) - Batch lifecycle, validation
- ConsumerServiceTest (11) - Latest price retrieval
- InMemoryPriceRepositoryTest (14) - Storage, history
- InMemoryBatchManagerTest (12) - Batch management
- LatestAsOfPriceSelectionStrategyTest (6) - asOf selection
- IntegrationTest (6) - End-to-end scenarios
- PriceTrackingServiceFactoryTest (6) - Factory pattern

**TestDataFactory:** Centralized test data with past timestamps

## Requirements Met

✅ Batch upload (1000 record chunks)  
✅ Atomic batch completion  
✅ Latest price by asOf timestamp  
✅ Batch cancellation  
✅ Thread-safe operations  
✅ Resilient to incorrect operation order  
✅ Price history (bonus feature)  

## Dependencies

- Java 17
- JUnit 5 (5.10.1)
- AssertJ (3.24.2)

## Design Patterns

- **Factory** - Service creation
- **Repository** - Data access abstraction
- **Strategy** - Price selection algorithm
- **Singleton** - Service instances

---