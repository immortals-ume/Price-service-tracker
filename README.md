# Price Tracking Service

A high-performance, in-memory price tracking service for financial instruments built with Java 17.

## Features

- **Batch Processing**: Upload prices in batches with chunks (max 1000 records per chunk)
- **Atomic Operations**: All prices in a batch become visible simultaneously
- **Latest Price Selection**: Automatically selects most recent price by asOf timestamp
- **Price History**: Stores full history, returns latest (bonus feature)
- **Thread-Safe**: ReadWriteLock for optimal concurrent performance
- **SOLID Design**: Clean architecture following SOLID principles

## Quick Start

### Build & Test
```bash
mvn clean install
mvn test
```

### Run Demo
```bash
mvn exec:java -Dexec.mainClass="com.fintech.pricetracking.PriceTrackingDemo"
```

## Usage Example

```java
// Get services
ProducerService producer = PriceTrackingServiceFactory.getProducerInstance();
ConsumerService consumer = PriceTrackingServiceFactory.getConsumerInstance();

// Producer: Upload prices
String batch = producer.startBatch();
producer.uploadChunk(batch, List.of(
    new PriceRecord("AAPL", Instant.now(), 150.50),
    new PriceRecord("GOOGL", Instant.now(), 2800.75)
));
producer.completeBatch(batch);

// Consumer: Get latest price
consumer.getLatestPrice("AAPL").ifPresent(price -> 
    System.out.println("AAPL: $" + price.payload()));
```

## API

### ProducerService
- `startBatch()` - Create new batch, returns batch ID
- `uploadChunk(batchId, records)` - Upload up to 1000 records
- `completeBatch(batchId)` - Publish all prices atomically
- `cancelBatch(batchId)` - Discard batch

### ConsumerService
- `getLatestPrice(instrumentId)` - Get latest price by asOf time

### PriceRecord
- `id` (String) - Instrument identifier
- `asOf` (Instant) - Price timestamp
- `payload` (Object) - Flexible price data

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

### Price Selection
- **Within batch**: Latest asOf wins (Strategy pattern)
- **Across batches**: Only newer asOf replaces existing
- **History**: All prices stored, latest returned

### Example
```
Batch 1: AAPL @ 10:05 = $155 → stored
Batch 2: AAPL @ 10:00 = $150 → added to history (older)
Batch 3: AAPL @ 10:10 = $160 → stored as latest
Consumer gets: $160 (latest)
History: [$160, $155, $150] (sorted newest first)
```

## Thread Safety

- **ReadWriteLock**: Multiple concurrent reads, exclusive writes
- **Atomic batch completion**: All-or-nothing visibility
- **ConcurrentHashMap**: Lock-free concurrent access

## Testing

**75 tests - 100% passing** ✅

| Test Suite | Tests | Coverage |
|------------|-------|----------|
| ProducerServiceTest | 19 | Batch lifecycle, chunks, validation |
| ConsumerServiceTest | 11 | Latest price retrieval |
| InMemoryPriceRepositoryTest | 14 | Storage, history, atomicity |
| InMemoryBatchManagerTest | 12 | Batch management |
| LatestAsOfPriceSelectionStrategyTest | 7 | asOf selection |
| IntegrationTest | 6 | End-to-end, concurrency |
| PriceTrackingServiceFactoryTest | 6 | Factory pattern |

### Run Tests
```bash
mvn test                                    # All tests
mvn test -Dtest=ProducerServiceTest        # Specific test
```

## Error Handling

- `InvalidBatchOperationException` - Invalid batch operations
- `InvalidChunkSizeException` - Chunk exceeds 1000 records
- `IllegalArgumentException` - Invalid parameters
- `NullPointerException` - Null required parameters

## Requirements Met

✅ Batch upload with 1000 record chunks  
✅ Atomic batch completion  
✅ Latest price by asOf timestamp  
✅ Batch cancellation  
✅ Thread-safe concurrent operations  
✅ Resilient to incorrect operation order  
✅ In-memory, zero external dependencies  
✅ Comprehensive test coverage  

## Dependencies

- Java 17
- JUnit 5 (5.10.1) - Testing
- AssertJ (3.24.2) - Assertions

## Design Patterns

- **Factory** - Service creation
- **Repository** - Data access
- **Strategy** - Price selection
- **Singleton** - Service instances

## SOLID Principles

- **SRP**: Each class has one responsibility
- **OCP**: Open for extension via interfaces
- **LSP**: Proper interface implementations
- **ISP**: Separate Producer/Consumer services
- **DIP**: Depend on abstractions

---

Built with ❤️ using Java 17, Maven, and SOLID principles.
