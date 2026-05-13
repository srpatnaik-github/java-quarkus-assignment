package com.fulfilment.application.monolith.warehouses.adapters;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.usecases.CreateWarehouseUseCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency integration tests — race conditions, thread safety,
 * DB constraint behaviour under concurrent load.
 *
 * FIXES APPLIED vs original:
 *
 *   1. @BeforeEach — added DB cleanup (DELETE FROM DbWarehouse).
 *      Without it, CONCURRENT-0..9 and READ-TEST-001 from a previous run
 *      still exist → TC-01 all 10 threads fail on duplicate code,
 *      TC-03 throws "already exists" on create.
 *
 *   2. TC-01 & TC-02 — thread tasks now call via ConcurrencyHelper CDI bean
 *      (@Transactional REQUIRES_NEW per thread). Without a transaction,
 *      Panache persist() throws TransactionRequiredException — the test
 *      "passed" only by coincidence on lenient TX configurations.
 *
 *   3. TC-03 — removed @Transactional from the test method + created the
 *      warehouse in a REQUIRES_NEW transaction that COMMITS before spawning
 *      reader threads. Original bug: @Transactional on the test method means
 *      the warehouse is created but NOT YET COMMITTED when reader threads
 *      start. JPA transactions are thread-bound — spawned threads cannot see
 *      data from an uncommitted TX on the main thread. All 20 reads returned
 *      null → successfulReads=0, assertion failed.
 *
 *   4. TC-03 reads — routed through ConcurrencyHelper.readWarehouse() with
 *      REQUIRES_NEW so each reader thread has its own valid transaction context.
 *
 *   5. Fixed raw List<Future>/Future → List<Future<Boolean>>/Future<Boolean>.
 */
@QuarkusTest
public class WarehouseConcurrencyIT {

  @Inject
  WarehouseRepository warehouseRepository;

  @Inject
  LocationGateway locationResolver;

  @Inject
  EntityManager em;

  @Inject
  ConcurrencyHelper concurrencyHelper;

  private CreateWarehouseUseCase createWarehouseUseCase;

  /**
   * FIX: Added DELETE FROM DbWarehouse.
   * Without cleanup, codes from a prior run (CONCURRENT-0..9, READ-TEST-001)
   * still exist — causing duplicate-code failures in TC-01 and TC-03.
   */
  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
    createWarehouseUseCase = new CreateWarehouseUseCase(warehouseRepository, locationResolver);
  }

  // ─────────────────────────────────────────────
  // TC-01: Concurrent creation with unique codes — all must succeed
  // ─────────────────────────────────────────────

  /**
   * 10 threads each create a warehouse with a unique code simultaneously.
   * All 10 must succeed — no false failures from race conditions.
   *
   * FIX: Each thread's task runs via ConcurrencyHelper.createWarehouseInNewTransaction()
   * which is @Transactional(REQUIRES_NEW). Without this, Panache persist()
   * throws TransactionRequiredException on raw executor threads.
   */
  @Test
  public void testConcurrentWarehouseCreationWithUniqueCodesSucceeds()
          throws InterruptedException {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    List<Future<Boolean>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      Future<Boolean> future = executor.submit(() -> {
        try {
          concurrencyHelper.createWarehouseInNewTransaction(
                  "CONCURRENT-" + index,
                  "AMSTERDAM-001",
                  50, 10
          );
          return true;
        } catch (Exception e) {
          return false;
        } finally {
          latch.countDown();
        }
      });
      futures.add(future);
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    long successCount = futures.stream().filter(f -> {
      try { return f.get(); } catch (Exception e) { return false; }
    }).count();

    assertEquals(threadCount, successCount,
            "All " + threadCount + " concurrent creations with unique codes should succeed");
  }

  // ─────────────────────────────────────────────
  // TC-02: Concurrent creation with duplicate code — exactly 1 must succeed
  // ─────────────────────────────────────────────

  /**
   * 5 threads all try to create a warehouse with the SAME businessUnitCode.
   * Exactly 1 must succeed (first to commit wins); the rest must fail with
   * either an application-level "already exists" error or a DB constraint violation.
   *
   * FIX: Via ConcurrencyHelper — each thread has its own REQUIRES_NEW TX
   * so the application-level findByBusinessUnitCode() check and persist()
   * happen atomically within a proper transaction boundary.
   */
  @Test
  public void testConcurrentWarehouseCreationWithDuplicateCodeFails()
          throws InterruptedException {
    int threadCount = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    String duplicateCode = "DUPLICATE-CODE-CONCURRENT";

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          concurrencyHelper.createWarehouseInNewTransaction(
                  duplicateCode,
                  "ZWOLLE-001",
                  30, 5
          );
          successCount.incrementAndGet();
        } catch (Exception e) {
          // Expected — duplicate application check or DB constraint
          failureCount.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // Exactly 1 warehouse with this code must exist in DB
    Warehouse persisted = warehouseRepository.findByBusinessUnitCode(duplicateCode);
    assertNotNull(persisted,
            "Exactly one warehouse with the duplicate code must be persisted");

    assertEquals(1, successCount.get(),
            "Only 1 thread should succeed — first to commit wins");
    assertEquals(threadCount - 1, failureCount.get(),
            "Remaining " + (threadCount - 1) + " threads should fail");
  }

  // ─────────────────────────────────────────────
  // TC-03: Concurrent reads — all 20 must succeed
  // ─────────────────────────────────────────────

  /**
   * FIX 1: Removed @Transactional from the test method.
   *        Original had @Transactional on the test — the warehouse was created
   *        inside TX-A (main thread) but NOT YET COMMITTED when reader threads
   *        started. JPA transactions are thread-bound — the 20 spawned reader
   *        threads had no access to TX-A's uncommitted data. All 20 reads
   *        returned null → successfulReads=0 → assertion failed.
   *
   * FIX 2: Warehouse created via concurrencyHelper.createWarehouseInNewTransaction()
   *        which uses @Transactional(REQUIRES_NEW) — commits BEFORE this line
   *        returns, so data is fully visible to all reader threads.
   *
   * FIX 3: Reads also routed through concurrencyHelper.readWarehouse() with
   *        REQUIRES_NEW — raw executor threads have no transaction context,
   *        so direct Panache reads can fail without one.
   */
  @Test
  public void testConcurrentReadsAreNonBlocking() throws InterruptedException {
    // FIX: Commits immediately via REQUIRES_NEW before any reader thread starts
    concurrencyHelper.createWarehouseInNewTransaction(
            "READ-TEST-001", "AMSTERDAM-001", 100, 50);

    int readThreadCount = 20;
    ExecutorService executor = Executors.newFixedThreadPool(readThreadCount);
    CountDownLatch latch = new CountDownLatch(readThreadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger successfulReads = new AtomicInteger(0);

    for (int i = 0; i < readThreadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await(); // all threads start simultaneously
          // FIX: read via CDI bean so each thread has a proper TX context
          Warehouse found = concurrencyHelper.readWarehouse("READ-TEST-001");
          if (found != null) {
            successfulReads.incrementAndGet();
          }
        } catch (Exception e) {
          // read failure — not expected
        } finally {
          latch.countDown();
        }
      });
    }

    startLatch.countDown(); // release all reader threads at once
    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(readThreadCount, successfulReads.get(),
            "All " + readThreadCount + " concurrent reads should succeed — " +
                    "data must be visible (committed before reader threads started)");
  }

  // ─────────────────────────────────────────────
  // CDI helper bean for transactional operations on executor threads
  // ─────────────────────────────────────────────

  /**
   * WHY a separate @ApplicationScoped bean is needed for thread pool tasks:
   *
   * Raw ExecutorService threads have NO CDI context and NO active transaction.
   * Calling @Transactional methods directly on `this` (self-invocation) or
   * instantiating use cases directly bypasses the CDI proxy — @Transactional
   * is silently ignored and Panache persist() throws TransactionRequiredException.
   *
   * By injecting this @ApplicationScoped bean and calling its methods,
   * every thread's call goes through the CDI proxy — @Transactional(REQUIRES_NEW)
   * starts a brand-new transaction for that thread and commits it when the
   * method returns normally, or rolls it back on exception.
   */
  @ApplicationScoped
  public static class ConcurrencyHelper {

    @Inject
    WarehouseRepository warehouseRepository;

    @Inject
    LocationGateway locationGateway;

    @Transactional(TxType.REQUIRES_NEW)
    public void createWarehouseInNewTransaction(String code, String location,
                                                int capacity, int stock) {
      CreateWarehouseUseCase useCase =
              new CreateWarehouseUseCase(warehouseRepository, locationGateway);

      Warehouse warehouse = new Warehouse();
      warehouse.businessUnitCode = code;
      warehouse.location         = location;
      warehouse.capacity         = capacity;
      warehouse.stock            = stock;
      warehouse.createdAt        = LocalDateTime.now();

      useCase.create(warehouse);
    }

    // FIX: Added — reader threads need their own TX context to call Panache safely
    @Transactional(TxType.REQUIRES_NEW)
    public Warehouse readWarehouse(String code) {
      return warehouseRepository.findByBusinessUnitCode(code);
    }
  }
}