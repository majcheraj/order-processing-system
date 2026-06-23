# Order Processing System

A production-grade e-commerce order processing backend built with **Kotlin + Spring Boot + PostgreSQL**, demonstrating real-world Java/Kotlin concurrency primitives in a business context.

---

## Tech Stack

- **Kotlin** + **Spring Boot 3.5.15**
- **PostgreSQL 17** (via JPA/Hibernate)
- **Spring Data JPA** with pessimistic and optimistic locking
- **SLF4J + Logback** for structured logging
- **Springdoc OpenAPI** (Swagger UI)
- **Spring Boot Actuator**
- **Docker + Docker Compose**

---

## Order Lifecycle
---

## API Endpoints

### Products
| Method | Path | Description |
|--------|------|-------------|
| POST | `/products` | Create a product |
| GET | `/products` | List all products (paginated) |
| GET | `/products/{id}` | Get product by ID |

### Orders
| Method | Path | Description |
|--------|------|-------------|
| POST | `/orders` | Create an order (returns 202 Accepted immediately) |
| GET | `/orders` | List all orders (paginated) |
| GET | `/orders/{id}` | Get order details |
| GET | `/orders/{id}/status` | Get order status (from in-memory cache, no DB hit) |
| POST | `/orders/{id}/cancel` | Cancel an order (before SHIPPED) |

### Internal
| Method | Path | Description |
|--------|------|-------------|
| GET | `/internal/concurrency-status` | Real-time concurrency metrics |

---

## Concurrency Architecture

This is the core of the project. Each primitive was chosen deliberately for its specific problem.

### 2.1 — ThreadPoolTaskExecutor (@Async)

**Problem:** Order processing (validation, payment, fulfillment) takes several seconds. Blocking the HTTP request thread would limit throughput to the number of Tomcat threads.

**Solution:** `POST /orders` returns `202 Accepted` immediately. Processing is delegated to a dedicated `ThreadPoolTaskExecutor` via Spring's `@Async`.

**Configuration:** `corePoolSize=4`, `maxPoolSize=8`, `queueCapacity=50`

**Interview talking point:** Pool sizing — core threads handle steady load, max threads handle bursts, queue provides backpressure. An unbounded pool (`maxPoolSize=MAX_INT`) is dangerous: each thread consumes ~512KB of stack memory; under high load it leads to `OutOfMemoryError`.

---

### 2.2 — Pessimistic Locking (`SELECT ... FOR UPDATE`)

**Problem:** Two orders racing for the last unit of a product must not both succeed (oversell).

**Solution:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `ProductRepository.findByIdWithLock()` generates `SELECT ... FOR UPDATE`. The second thread blocks until the first transaction commits, then sees the updated (possibly zero) stock.

**Why pessimistic here:** High contention (many orders for the same popular product), short critical section (read-check-decrement), correctness is non-negotiable.

**Deadlock prevention:** Items are always locked in consistent order (sorted by `productId`) before acquiring locks.

**Interview talking point:** Contrast with optimistic locking — pessimistic blocks upfront, optimistic detects conflict at write time. Different contention profiles require different strategies.

---

### 2.3 — Optimistic Locking (`@Version`)

**Problem:** Multiple background steps update the same order's status over time. Silent overwrites must be detected.

**Solution:** `@Version var version: Long` on the `Order` entity. Hibernate appends `AND version = ?` to every `UPDATE`. If the version doesn't match (another thread updated the row), `OptimisticLockException` is thrown and the update is retried (up to 3 times with 50ms backoff).

**Why optimistic here:** Orders rarely collide with each other (each order has its own processing thread). Pessimistic locking would add unnecessary overhead for a low-contention scenario.

---

### 2.4 — ConcurrentHashMap (In-Memory Status Cache)

**Problem:** Clients poll `GET /orders/{id}/status` frequently. Every poll hitting the database is wasteful.

**Solution:** `ConcurrentHashMap<UUID, OrderStatus>` updated on every status change. `GET /orders/{id}/status` reads from cache — zero DB queries.

**Cache warm-up:** On startup (`ApplicationRunner`), active orders (not DELIVERED/CANCELLED/FAILED) are loaded into the cache before Tomcat accepts requests.

**Why `ConcurrentHashMap`:** Thread-safe reads without locking, segment-level locking for writes. A `synchronized HashMap` would serialize all reads — unnecessary overhead.

**Trade-off:** Cache is in-memory only. On restart, it's rebuilt from DB via warm-up. Acceptable for this use case; production would use Redis.

---

### 2.5 — Semaphore (Payment Gateway Throttling)

**Problem:** The payment gateway can only handle 5 concurrent calls. Exceeding this causes errors or rate limiting.

**Solution:** `Semaphore(5, true)` — fair mode (FIFO), max 5 concurrent payment calls. `acquire()` blocks if all permits are taken; `release()` in `finally` guarantees the permit is always returned even on exception.

**Semaphore vs ReentrantLock:** `ReentrantLock` = one exclusive holder. `Semaphore(N)` = N simultaneous holders. `Semaphore(1)` ≈ mutex but non-reentrant.

**Fair mode:** `true` ensures FIFO ordering — no thread starvation. Cost: slightly lower throughput than unfair mode.

---

### 2.6 — CountDownLatch (Parallel Validation)

**Problem:** Order validation has 3 independent checks (stock availability, customer eligibility, price consistency). Running them sequentially wastes time.

**Solution:** All 3 checks are submitted to a dedicated `ThreadPoolExecutor` simultaneously. `CountDownLatch(3)` blocks the order-processing thread until all 3 checks `countDown()`. Total validation time = `max(200ms, 300ms, 150ms) = 300ms` instead of `650ms` sequential.

**`finally { latch.countDown() }`:** Critical — guarantees decrement even if a check throws, preventing the order thread from waiting forever (deadlock).

**CountDownLatch vs CyclicBarrier vs CompletableFuture.allOf:**
- `CountDownLatch` — one-shot, one thread waits, N threads count down
- `CyclicBarrier` — reusable, all parties wait for each other
- `CompletableFuture.allOf` — composable, integrates with thread pools, idiomatic modern Java/Kotlin

---

### 2.7 — BlockingQueue (Fulfillment Pipeline)

**Problem:** Fulfillment (packing and shipping) is a separate concern from payment. It should be handled by dedicated "warehouse worker" threads, not the order-processing pool.

**Solution:** `LinkedBlockingQueue<UUID>(100)` as a producer/consumer pipeline. Order-processing threads `offer()` paid order IDs to the queue. 3 warehouse worker threads (started via `@PostConstruct`) continuously `poll()` from the queue and process fulfillment.

**Bounded queue (capacity=100):** Provides backpressure — if warehouse workers fall behind, `offer()` with timeout either waits or logs a warning, preventing unbounded memory growth.

**Graceful shutdown (`@PreDestroy`):** Sets `running = false`, workers drain naturally and exit their loop. `join(2000)` waits up to 2 seconds per worker for clean exit.

**`LinkedBlockingQueue` vs `ArrayBlockingQueue`:** `LinkedBlockingQueue` has separate head/tail locks — higher throughput for producer/consumer scenarios. `ArrayBlockingQueue` uses one lock — more predictable memory (pre-allocated array).

**Production note:** For true production scale, replace `LinkedBlockingQueue` with **Apache Kafka** — persistent, horizontally scalable, replayable. This project includes a Kafka implementation as an upgrade (see Kafka section below).

---

## Running Locally

### Prerequisites
- Java 21 (Eclipse Temurin recommended)
- Docker Desktop
- PostgreSQL 17 (or use Docker Compose)

### With Docker Compose
```bash
docker-compose up -d
```

### Without Docker (local PostgreSQL)
1. Create database: `order_processing_db`
2. Set environment variable: `DB_PASSWORD=your_password`
3. Run: `./gradlew bootRun`

### Swagger UI

http://localhost:8080/swagger-ui/index.html

## Project Structure

src/main/kotlin/com/orderprocessing/

├── cache/          # ConcurrentHashMap order status cache

├── config/         # AsyncConfig (ThreadPoolTaskExecutor), CacheWarmUpConfig

├── controller/     # REST controllers + ConcurrencyStatusController

├── dto/            # Request/Response DTOs

├── entity/         # JPA entities (Order, Product, OrderItem)

├── exception/      # Custom exceptions + GlobalExceptionHandler

├── repository/     # Spring Data JPA repositories

└── service/        # Business logic

├── OrderService.kt          # @Async processing, optimistic lock retry

├── StockService.kt          # Pessimistic locking

├── ValidationService.kt     # CountDownLatch parallel validation

├── PaymentGatewayService.kt # Semaphore throttling

└── FulfillmentService.kt    # BlockingQueue pipeline