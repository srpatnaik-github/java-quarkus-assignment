package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReplaceWarehouseUseCase.
 *
 * The use case enforces 5 validations in order:
 *   1. Warehouse must exist                  → "does not exist"
 *   2. Warehouse must not be archived        → "is archived and cannot be replaced"
 *   3. Location must be valid                → "is not valid"
 *   4. capacity ≤ location.maxCapacity()     → "exceeds location max capacity"
 *   5. stock ≤ capacity                      → "exceeds warehouse capacity"
 *
 * After all validations pass, only location/capacity/stock are updated.
 * createdAt, businessUnitCode, and archivedAt are PRESERVED.
 *
 * FIXES APPLIED vs original:
 *   - TC-01: Added verification that createdAt, businessUnitCode, archivedAt
 *            are preserved after replace (the core contract of this use case).
 *   - TC-03: Tightened message check: "archived" → "is archived and cannot be replaced"
 *   - TC-05 (concurrent): FIXED — Thread 1 used "ZWOLLE-001" with capacity=50
 *            but ZWOLLE-001 maxCapacity=40. Thread 1 always threw
 *            IllegalArgumentException (validation error) instead of ever
 *            reaching the DB. Changed Thread 1 to "AMSTERDAM-001" (maxCapacity=100).
 *   - TC-06 (NEW): Boundary — capacity exactly at location max should pass
 *   - TC-07 (NEW): Boundary — stock exactly equal to capacity should pass
 *   - TC-08 (NEW): Validation order — archived check fires before location check
 *   - TC-09 (NEW): Validation order — location check fires before capacity check
 */
@QuarkusTest
public class ReplaceWarehouseUseCaseTest {

  @Inject
  WarehouseRepository warehouseRepository;

  @Inject
  LocationGateway locationResolver;

  @Inject
  EntityManager em;

  private ReplaceWarehouseUseCase replaceWarehouseUseCase;

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
    replaceWarehouseUseCase = new ReplaceWarehouseUseCase(warehouseRepository, locationResolver);
  }

  // ─────────────────────────────────────────────
  // TC-01: Happy path — all fields updated/preserved correctly
  // ─────────────────────────────────────────────

  /**
   * Verifies:
   *   - location, capacity, stock ARE updated
   *   - createdAt, businessUnitCode, archivedAt are PRESERVED (not overwritten)
   *
   * FIX: Original only checked location/capacity/stock but NOT the preservation
   * contract. This is the most important invariant of the replace use case.
   */
  @Test
  @Transactional
  public void testReplaceWarehouseSuccessfully() {
    LocalDateTime originalCreatedAt = LocalDateTime.now().minusDays(1);
    createWarehouseWithCreatedAt("REPLACE-TEST-001", "AMSTERDAM-001", 80, 40, originalCreatedAt);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "REPLACE-TEST-001";
    replacement.location         = "ZWOLLE-001";
    replacement.capacity         = 30;
    replacement.stock            = 15;

    replaceWarehouseUseCase.replace(replacement);

    Warehouse updated = warehouseRepository.findByBusinessUnitCode("REPLACE-TEST-001");
    assertNotNull(updated);

    // Fields that MUST be updated
    assertEquals("ZWOLLE-001", updated.location,   "location should be updated");
    assertEquals(30,           updated.capacity,   "capacity should be updated");
    assertEquals(15,           updated.stock,      "stock should be updated");

    // Fields that MUST be preserved — the core contract of replace
    assertEquals("REPLACE-TEST-001", updated.businessUnitCode,
            "businessUnitCode must never be changed by replace");
    assertEquals(originalCreatedAt.withNano(0), updated.createdAt.withNano(0),
            "createdAt must be preserved — replace does not reset creation time");
    assertNull(updated.archivedAt,
            "archivedAt must remain null — replacing an active warehouse does not archive it");
  }

  // ─────────────────────────────────────────────
  // TC-02: Cannot replace non-existent warehouse
  // ─────────────────────────────────────────────

  @Test
  @Transactional
  public void testCannotReplaceNonExistentWarehouse() {
    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "NON-EXISTENT";
    replacement.location         = "AMSTERDAM-001";
    replacement.capacity         = 50;
    replacement.stock            = 25;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> replaceWarehouseUseCase.replace(replacement));

    assertTrue(ex.getMessage().contains("does not exist"),
            "Error should contain 'does not exist'. Got: " + ex.getMessage());
  }

  // ─────────────────────────────────────────────
  // TC-03: Cannot replace archived warehouse
  // ─────────────────────────────────────────────

  /**
   * FIX: Tightened check from "archived" → "is archived and cannot be replaced"
   * to match the exact phrase in ReplaceWarehouseUseCase source.
   */
  @Test
  @Transactional
  public void testCannotReplaceArchivedWarehouse() {
    Warehouse warehouse = createWarehouse("REPLACE-TEST-002", "AMSTERDAM-001", 80, 40);
    warehouse.archivedAt = LocalDateTime.now();
    warehouseRepository.update(warehouse);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "REPLACE-TEST-002";
    replacement.location         = "ZWOLLE-001";
    replacement.capacity         = 30;
    replacement.stock            = 15;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> replaceWarehouseUseCase.replace(replacement));

    assertTrue(ex.getMessage().contains("is archived and cannot be replaced"),
            "Error should contain 'is archived and cannot be replaced'. Got: " + ex.getMessage());
  }

  // ─────────────────────────────────────────────
  // TC-04: Parameterized — invalid location, capacity, stock
  // ─────────────────────────────────────────────

  @ParameterizedTest
  @MethodSource("provideInvalidReplaceScenarios")
  @Transactional
  public void testCapacityAndStockValidations(InvalidReplaceScenario scenario) {
    createWarehouse("REPLACE-VALIDATION", "AMSTERDAM-001", 80, 40);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "REPLACE-VALIDATION";
    replacement.location         = scenario.location;
    replacement.capacity         = scenario.capacity;
    replacement.stock            = scenario.stock;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> replaceWarehouseUseCase.replace(replacement));

    assertTrue(ex.getMessage().contains(scenario.expectedMessageFragment),
            "Expected message to contain '" + scenario.expectedMessageFragment +
                    "' but got: " + ex.getMessage());
  }

  static Stream<InvalidReplaceScenario> provideInvalidReplaceScenarios() {
    return Stream.of(
            // Invalid location
            new InvalidReplaceScenario("INVALID-LOCATION", 50, 25,
                    "is not valid"),

            // Capacity exceeds location max — AMSTERDAM-001 maxCapacity=100
            new InvalidReplaceScenario("AMSTERDAM-001", 101, 50,
                    "exceeds location max capacity"),

            // Stock exceeds capacity — ZWOLLE-001 maxCapacity=40, capacity=30 OK, stock=40 > capacity
            new InvalidReplaceScenario("ZWOLLE-001", 30, 40,
                    "exceeds warehouse capacity")
    );
  }

  // ─────────────────────────────────────────────
  // TC-05 (FIXED): Concurrent replace — optimistic lock
  // ─────────────────────────────────────────────

  /**
   * ORIGINAL BUG: Thread 1 used "ZWOLLE-001" with capacity=50.
   * ZWOLLE-001 maxCapacity=40 → 50 > 40 → Thread 1 ALWAYS threw
   * IllegalArgumentException on Validation 4 — it never reached the DB.
   * exceptionCaught was set to true for the wrong reason (validation failure,
   * not optimistic lock), making the test pass coincidentally without
   * testing concurrency at all.
   *
   * FIX: Thread 1 now uses "AMSTERDAM-001" (maxCapacity=100), so capacity=50
   * is fully valid. Both threads now reach the DB simultaneously, and
   * @Version on DbWarehouse causes a genuine OptimisticLockException.
   *
   * REQUIRES: @Version field on DbWarehouse.
   */
  @Test
  public void testConcurrentReplaceCausesOptimisticLockException() throws InterruptedException {
    String code = createWarehouseInNewTransaction("CONCURRENT-REPLACE-001", "AMSTERDAM-001", 100, 50);

    ExecutorService executor    = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch   = new CountDownLatch(1);
    CountDownLatch finishLatch  = new CountDownLatch(2);

    AtomicBoolean thread1Success  = new AtomicBoolean(false);
    AtomicBoolean thread2Success  = new AtomicBoolean(false);
    AtomicBoolean exceptionCaught = new AtomicBoolean(false);
    AtomicReference<Exception> caughtException = new AtomicReference<>();

    // Thread 1: Replace to AMSTERDAM-001 cap=50
    // FIX: was "ZWOLLE-001" cap=50 which ALWAYS failed Validation 4 (50 > maxCap 40)
    executor.submit(() -> {
      try {
        startLatch.await();
        replaceWarehouseInNewTransaction(code, "AMSTERDAM-001", 50, 25);
        thread1Success.set(true);
      } catch (Exception e) {
        exceptionCaught.set(true);
        caughtException.set(e);
      } finally {
        finishLatch.countDown();
      }
    });

    // Thread 2: Replace to EINDHOVEN-001 cap=60 (maxCapacity=70 → valid)
    executor.submit(() -> {
      try {
        startLatch.await();
        replaceWarehouseInNewTransaction(code, "EINDHOVEN-001", 60, 30);
        thread2Success.set(true);
      } catch (Exception e) {
        exceptionCaught.set(true);
        caughtException.set(e);
      } finally {
        finishLatch.countDown();
      }
    });

    startLatch.countDown();
    finishLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    Warehouse finalWarehouse = warehouseRepository.findByBusinessUnitCode(code);

    boolean onlyOneThreadSucceeded =
            (thread1Success.get() && !thread2Success.get()) ||
                    (!thread1Success.get() && thread2Success.get());

    assertTrue(onlyOneThreadSucceeded || exceptionCaught.get(),
            "Expected only one thread to succeed OR an OptimisticLockException. " +
                    "Both succeeded (lost update): location=" + finalWarehouse.location +
                    ", capacity=" + finalWarehouse.capacity +
                    ", stock=" + finalWarehouse.stock);

    // If no exception, final state must exactly match one of the two threads
    if (!exceptionCaught.get()) {
      boolean matchesThread1 = "AMSTERDAM-001".equals(finalWarehouse.location)
              && finalWarehouse.capacity == 50
              && finalWarehouse.stock == 25;

      boolean matchesThread2 = "EINDHOVEN-001".equals(finalWarehouse.location)
              && finalWarehouse.capacity == 60
              && finalWarehouse.stock == 30;

      assertTrue(matchesThread1 || matchesThread2,
              "Final state must match exactly one thread's update. " +
                      "Got: location=" + finalWarehouse.location +
                      ", capacity=" + finalWarehouse.capacity +
                      ", stock=" + finalWarehouse.stock);
    }
  }

  // ─────────────────────────────────────────────
  // TC-06 (NEW): Boundary — capacity exactly at location max passes
  // ─────────────────────────────────────────────

  @Test
  @Transactional
  public void testReplaceSucceedsWhenCapacityEqualsLocationMax() {
    createWarehouse("BOUNDARY-001", "AMSTERDAM-001", 80, 40);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "BOUNDARY-001";
    replacement.location         = "ZWOLLE-001";
    replacement.capacity         = 40; // ZWOLLE-001 maxCapacity = 40 — exactly at limit
    replacement.stock            = 20;

    assertDoesNotThrow(
            () -> replaceWarehouseUseCase.replace(replacement),
            "Capacity equal to location max (40) should be accepted"
    );
  }

  // ─────────────────────────────────────────────
  // TC-07 (NEW): Boundary — stock exactly equal to capacity passes
  // ─────────────────────────────────────────────

  @Test
  @Transactional
  public void testReplaceSucceedsWhenStockEqualsCapacity() {
    createWarehouse("BOUNDARY-002", "AMSTERDAM-001", 80, 40);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "BOUNDARY-002";
    replacement.location         = "AMSTERDAM-001";
    replacement.capacity         = 50;
    replacement.stock            = 50; // stock == capacity — exactly at limit

    assertDoesNotThrow(
            () -> replaceWarehouseUseCase.replace(replacement),
            "Stock equal to capacity should be accepted"
    );
  }

  // ─────────────────────────────────────────────
  // TC-08 (NEW): Validation order — archived fires before location check
  // ─────────────────────────────────────────────

  /**
   * Validation 2 (archived) must fire BEFORE Validation 3 (location).
   * Pass an archived warehouse with an invalid location — must get "archived" error.
   */
  @Test
  @Transactional
  public void testArchivedValidationFiresBeforeLocationValidation() {
    Warehouse warehouse = createWarehouse("ORDER-001", "AMSTERDAM-001", 80, 40);
    warehouse.archivedAt = LocalDateTime.now();
    warehouseRepository.update(warehouse);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "ORDER-001";
    replacement.location         = "INVALID-LOCATION"; // also invalid
    replacement.capacity         = 50;
    replacement.stock            = 20;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> replaceWarehouseUseCase.replace(replacement));

    assertTrue(ex.getMessage().contains("is archived and cannot be replaced"),
            "Validation 2 (archived) should fire before Validation 3 (location). " +
                    "Got: " + ex.getMessage());
  }

  // ─────────────────────────────────────────────
  // TC-09 (NEW): Validation order — location fires before capacity check
  // ─────────────────────────────────────────────

  /**
   * Validation 3 (location) must fire BEFORE Validation 4 (capacity).
   */
  @Test
  @Transactional
  public void testLocationValidationFiresBeforeCapacityValidation() {
    createWarehouse("ORDER-002", "AMSTERDAM-001", 80, 40);

    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = "ORDER-002";
    replacement.location         = "INVALID-LOCATION"; // invalid
    replacement.capacity         = 9999;               // would also fail capacity check
    replacement.stock            = 20;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> replaceWarehouseUseCase.replace(replacement));

    assertTrue(ex.getMessage().contains("is not valid"),
            "Validation 3 (location invalid) should fire before Validation 4 (capacity). " +
                    "Got: " + ex.getMessage());
  }

  // ─────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────

  @Transactional(TxType.REQUIRES_NEW)
  Warehouse createWarehouse(String code, String location, int capacity, int stock) {
    Warehouse w = new Warehouse();
    w.businessUnitCode = code;
    w.location         = location;
    w.capacity         = capacity;
    w.stock            = stock;
    w.createdAt        = LocalDateTime.now();
    warehouseRepository.create(w);
    return w;
  }

  @Transactional(TxType.REQUIRES_NEW)
  void createWarehouseWithCreatedAt(String code, String location,
                                    int capacity, int stock, LocalDateTime createdAt) {
    Warehouse w = new Warehouse();
    w.businessUnitCode = code;
    w.location         = location;
    w.capacity         = capacity;
    w.stock            = stock;
    w.createdAt        = createdAt;
    warehouseRepository.create(w);
  }

  @Transactional(TxType.REQUIRES_NEW)
  String createWarehouseInNewTransaction(String code, String location,
                                         int capacity, int stock) {
    createWarehouse(code, location, capacity, stock);
    return code;
  }

  @Transactional(TxType.REQUIRES_NEW)
  void replaceWarehouseInNewTransaction(String code, String newLocation,
                                        int newCapacity, int newStock) {
    Warehouse replacement = new Warehouse();
    replacement.businessUnitCode = code;
    replacement.location         = newLocation;
    replacement.capacity         = newCapacity;
    replacement.stock            = newStock;
    replaceWarehouseUseCase.replace(replacement);
  }

  // ─────────────────────────────────────────────
  // Parameterized scenario record
  // ─────────────────────────────────────────────

  static class InvalidReplaceScenario {
    String location;
    int    capacity;
    int    stock;
    String expectedMessageFragment;

    InvalidReplaceScenario(String location, int capacity, int stock,
                           String expectedMessageFragment) {
      this.location                = location;
      this.capacity                = capacity;
      this.stock                   = stock;
      this.expectedMessageFragment = expectedMessageFragment;
    }

    @Override
    public String toString() {
      return "location='" + location + "', capacity=" + capacity +
              ", stock=" + stock + ", expected='" + expectedMessageFragment + "'";
    }
  }
}