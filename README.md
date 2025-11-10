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

### BatchAudit (History)
- `batchId` - Unique batch identifier
- `completedAt` - Completion timestamp
- `recordCount` - Number of records in batch
- `status` - COMPLETED or CANCELLED

## System Flow

```
PRODUCER FLOW:
==============

1. startBatch()
   └─> BatchManager.createBatch()
       └─> Creates UUID
       └─> Stores in: Map<String, BatchSession> (staging)
       └─> Returns batchId

2. uploadChunk(batchId, records)  [Can be called multiple times]
   └─> Validates: records != null, size <= 1000
   └─> BatchManager.addRecords(batchId, records)
       └─> Adds to: BatchSession.records (List)
       └─> Still in STAGING - NOT visible to consumers

3. completeBatch(batchId)
   └─> BatchManager.getBatchRecords(batchId)
       └─> Returns: List<PriceRecord> (all records from all chunks)
   └─> PriceRepository.saveAllRecords(records)
       └─> For each record:
           └─> storage.compute(instrumentId, addToHistory())
               └─> Adds to: Map<String, List<PriceRecord>>
                   ├─> Key: instrumentId (e.g., "AAPL")
                   └─> Value: List sorted by asOf (newest first)
   └─> BatchManager.removeBatch(batchId)
       └─> Removes from staging

CONSUMER FLOW:
==============

getLatestPrice(instrumentId)
└─> PriceRepository.findByInstrumentId(instrumentId)
    └─> storage.get(instrumentId)
        └─> Returns: history.get(0)  [First = Latest by asOf]


STORAGE STRUCTURE:
==================

BatchManager (Staging):
Map<String, BatchSession>
├─> "batch-uuid-1" → BatchSession
│   └─> records: [AAPL@10:00, AAPL@10:05, GOOGL@10:00]
└─> "batch-uuid-2" → BatchSession
    └─> records: [...]

BatchManager (History/Audit):
List<BatchAudit>
├─> BatchAudit(batchId="uuid-1", status=COMPLETED, count=3, time=...)
├─> BatchAudit(batchId="uuid-2", status=CANCELLED, count=2, time=...)
└─> BatchAudit(batchId="uuid-3", status=COMPLETED, count=1500, time=...)

PriceRepository (Final Storage):
Map<String, List<PriceRecord>>
├─> "AAPL" → [10:05, 10:00, 09:00]  ← Sorted newest first
├─> "GOOGL" → [11:00, 10:00]
└─> "MSFT" → [10:30]
```

## Design Decisions

### 1. History-Based Storage
**Decision:** Store ALL prices per instrument, return latest

**Why:** 
- Enables auditing and compliance
- Supports historical analysis
- Consumer API unchanged (still gets latest)
- Repository: `Map<InstrumentId, List<PriceRecord>>` sorted by asOf (newest first)
- No filtering - full audit trail preserved

### 2. Past Timestamps
**Decision:** Use past timestamps (`.minusSeconds()`) in tests and demo

**Why:**
- Realistic - prices determined in the past, not future
- Demo: `now.minusSeconds(30)` = 30 seconds ago
- Tests: Use `TestDataFactory` for consistent past timestamps

### 3. Atomic Batch Completion
**Decision:** All prices in batch visible simultaneously

**Implementation:**
- `saveAll()` uses write lock
- Consumers blocked during write
- See all prices or none

**Why:** Business requirement - "all prices should be made available at the same time"

### 4. Thread Safety (ReadWriteLock)
**Decision:** Use ReadWriteLock instead of synchronized

**Why:**
- Multiple concurrent reads (no blocking)
- Exclusive writes (atomic updates)
- Better performance for read-heavy workloads

### 5. Separated Producer/Consumer Services
**Decision:** Two separate service interfaces

**Why:**
- Interface Segregation Principle (ISP)
- Producers don't need consumer methods
- Consumers don't need producer methods
- Clear separation of concerns

### 6. TestDataFactory Utility
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
├── batch/          # Batch staging management
└── exception/      # Custom exceptions
```

## Key Behaviors

**Chunk Upload:** Sequential (synchronous), max 1000 records per chunk  
**All Records Saved:** No filtering - complete audit trail  
**Latest Selection:** Repository maintains sorted history (newest first)  
**Consumer Gets:** First element in list (latest by asOf)  
**Thread Safety:** ReadWriteLock for concurrent reads, exclusive writes

**Example:**
```
Batch 1: AAPL @ 10:05 = $155, AAPL @ 10:00 = $150
         → Both saved to history: [10:05, 10:00]
         
Batch 2: AAPL @ 10:10 = $160, AAPL @ 09:00 = $145
         → Both added to history: [10:10, 10:05, 10:00, 09:00]
         
Consumer gets: $160 @ 10:10 (latest)
Full history: [$160@10:10, $155@10:05, $150@10:00, $145@09:00]
```

## Testing

**72 tests - 100% passing**

- ProducerServiceTest (17) - Batch lifecycle, validation, 1000 chunk size
- ConsumerServiceTest (11) - Latest price retrieval
- InMemoryPriceRepositoryTest (14) - Storage, history
- InMemoryBatchManagerTest (12) - Batch management
- BatchHistoryTest (5) - Batch audit trail
- IntegrationTest (6) - End-to-end scenarios
- PriceTrackingServiceFactoryTest (6) - Factory pattern

**TestDataFactory:** Centralized test data with past timestamps

## Requirements Met

✅ Batch upload (1000 record chunks) - Sequential  
✅ Atomic batch completion  
✅ Latest price by asOf timestamp  
✅ Batch cancellation  
✅ Thread-safe operations (ReadWriteLock)  
✅ Resilient to incorrect operation order  
✅ Comprehensive logging (SLF4J + Logback)  
✅ Complete validation with test coverage  
✅ Price history per instrument (bonus)  
✅ Batch audit trail (bonus)  

## Logging

The application uses **SLF4J** with **Logback** for comprehensive logging:

**Log Levels:**
- `INFO` - Batch lifecycle events (start, complete, cancel)
- `INFO` - Repository operations (records saved, instruments added)
- `DEBUG` - Detailed operations (chunk uploads, price lookups)
- `ERROR` - Validation failures and exceptions
- `WARN` - Unusual conditions (non-existent batch removal)

**Key Log Events:**
```
INFO  - Batch started: {batchId}
INFO  - Completing batch {batchId}: {count} total records
INFO  - Saved {count} records ({new} new instruments) to repository
INFO  - Batch completed successfully: {batchId}
INFO  - Batch cancelled: {batchId}
ERROR - Upload chunk failed: batch {batchId} not found
```

**Configuration:**
- Production: `src/main/resources/logback.xml` (INFO level)
- Tests: `src/test/resources/logback-test.xml` (WARN level - quieter)

## Dependencies

- Java 17
- JUnit 5 (5.10.1)
- AssertJ (3.24.2)
- SLF4J (2.0.9)
- Logback (1.4.14)

## Design Patterns & Principles

**Patterns:**
- **Factory** - Service creation
- **Repository** - Data access abstraction
- **Singleton** - Service instances (Bill Pugh holder idiom)

**SOLID Principles:**
- **SRP** - Each class has single responsibility
- **OCP** - Open for extension, closed for modification
- **LSP** - Interfaces properly implemented
- **ISP** - Separate Producer/Consumer interfaces
- **DIP** - Depend on abstractions, not implementations

---


## Alternatively you can use spring batch 
