package com.fulfilment.application.monolith.warehouses.adapters;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.usecases.CreateWarehouseUseCase;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database integration tests using Quarkus Dev Services (real PostgreSQL via Testcontainers).
 * Tests DB-level constraints, JPQL queries, NULL handling, and transaction rollback.
 *
 * FIXES APPLIED vs original:
 *
 *   1. testTransactionRollbackDoesNotPersist — CRITICAL FIX.
 *      Original used self-invocation: this.performFailingTransaction().
 *      In Quarkus CDI, @Transactional is implemented via a CDI proxy.
 *      Calling a @Transactional method on `this` bypasses the proxy entirely
 *      — the annotation is silently ignored, no transaction is started,
 *      and the RuntimeException does NOT trigger a rollback.
 *      If Panache's persist() auto-commits, the warehouse IS persisted
 *      and assertNull() FAILS.
 *
 *      FIX: Extracted performFailingTransaction() into a separate
 *      @ApplicationScoped bean (TransactionHelper). Calls to another
 *      CDI bean go through the proxy — @Transactional(REQUIRES_NEW)
 *      fires correctly and RuntimeException causes a genuine rollback.
 *
 *   2. testQueryingMultipleWarehousesAtSameLocation — changed
 *      assertTrue(all.size() >= 5) → assertEquals(5, all.size()).
 *      After @BeforeEach cleanup, exactly 5 warehouses exist — the loose
 *      >= 5 check is imprecise and passes even with leaked ghost records.
 *
 *   3. testNullFieldsHandling — changed location from "ZWOLLE-001" cap=50
 *      (50 > maxCapacity 40, inconsistent) to "AMSTERDAM-001" cap=50 (valid).
 *      em.persist() bypasses validation so it won't throw, but the stored
 *      data should not violate business rules.
 *
 *   4. testComplexQueryByLocationAndCapacity — fixed raw List to List<DbWarehouse>.
 */
@QuarkusTest
public class WarehouseTestcontainersIT {

  @Inject
  WarehouseRepository warehouseRepository;

  @Inject
  LocationGateway locationResolver;

  @Inject
  EntityManager em;

  @Inject
  TransactionHelper transactionHelper;  // FIX: injected CDI bean for rollback test

  private CreateWarehouseUseCase createWarehouseUseCase;

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
    createWarehouseUseCase = new CreateWarehouseUseCase(warehouseRepository, locationResolver);
  }

  // ─────────────────────────────────────────────
  // TC-01: DB unique constraint on businessUnitCode
  // ─────────────────────────────────────────────

  /**
   * Verifies the DB-level UNIQUE constraint on businessUnitCode.
   * Bypasses the application layer (use case) to test the constraint directly
   * via EntityManager — proves the constraint exists in the schema, not just
   * in application code.
   */
  @Test
  @Transactional
  public void testDatabaseUniqueConstraintOnBusinessUnitCode() {
    Warehouse warehouse1 = new Warehouse();
    warehouse1.businessUnitCode = "DB-UNIQUE-001";
    warehouse1.location         = "AMSTERDAM-001";
    warehouse1.capacity         = 50;
    warehouse1.stock            = 10;
    warehouse1.createdAt        = LocalDateTime.now();
    createWarehouseUseCase.create(warehouse1);

    // Bypass use case — insert duplicate directly at DB level
    DbWarehouse dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = "DB-UNIQUE-001"; // duplicate
    dbWarehouse.location         = "ZWOLLE-001";
    dbWarehouse.capacity         = 30;
    dbWarehouse.stock            = 5;
    dbWarehouse.createdAt        = LocalDateTime.now();

    assertThrows(Exception.class, () -> {
      em.persist(dbWarehouse);
      em.flush(); // flush forces DB constraint check
    }, "DB unique constraint must reject duplicate businessUnitCode");
  }

  // ─────────────────────────────────────────────
  // TC-02: Query multiple warehouses at same location
  // ─────────────────────────────────────────────

  /**
   * FIX: Changed assertTrue(all.size() >= 5) → assertEquals(5, all.size()).
   * After @BeforeEach cleanup + 5 creates, exactly 5 warehouses exist.
   * The loose >= 5 passes even with ghost data from other tests leaking in.
   */
  @Test
  @Transactional
  public void testQueryingMultipleWarehousesAtSameLocation() {
    for (int i = 0; i < 5; i++) {
      Warehouse warehouse = new Warehouse();
      warehouse.businessUnitCode = "QUERY-TEST-" + i;
      warehouse.location         = "AMSTERDAM-001";
      warehouse.capacity         = 20 + (i * 10);
      warehouse.stock            = 5 + i;
      createWarehouseUseCase.create(warehouse);
    }

    List<Warehouse> all = warehouseRepository.getAll();

    // FIX: exact count — not >= 5
    assertEquals(5, all.size(),
            "After clean setup + 5 creates, exactly 5 warehouses should exist");

    long amsterdamCount = all.stream()
            .filter(w -> "AMSTERDAM-001".equals(w.location))
            .count();

    assertEquals(5, amsterdamCount,
            "All 5 warehouses should be at AMSTERDAM-001");
  }

  // ─────────────────────────────────────────────
  // TC-03: NULL archivedAt is stored and retrieved correctly
  // ─────────────────────────────────────────────

  /**
   * FIX: Changed location from "ZWOLLE-001" cap=50 (50 > maxCap 40 — inconsistent)
   * to "AMSTERDAM-001" cap=50 (maxCap=100 — valid).
   * em.persist() bypasses use case validation so it won't throw, but stored
   * data should not violate business rules.
   */
  @Test
  @TestTransaction  // ← replaces @Transactional, auto-rolls back after test
  public void testNullFieldsHandling() {
    DbWarehouse dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = "NULL-TEST-001";
    dbWarehouse.location         = "AMSTERDAM-001"; // FIX: was ZWOLLE-001 cap=50 (>maxCap 40)
    dbWarehouse.capacity         = 50;
    dbWarehouse.stock            = 10;
    dbWarehouse.createdAt        = LocalDateTime.now();
    dbWarehouse.archivedAt       = null;

    em.persist(dbWarehouse);
    em.flush();

    DbWarehouse found = em.find(DbWarehouse.class, dbWarehouse.id);
    assertNotNull(found, "Warehouse should be found after persist");
    assertNull(found.archivedAt,
            "archivedAt must remain null for an active warehouse");
  }

  // ─────────────────────────────────────────────
  // TC-04: Transaction rollback does not persist the warehouse
  // ─────────────────────────────────────────────

  /**
   * CRITICAL FIX — self-invocation bypasses @Transactional in Quarkus CDI.
   *
   * Original: this.performFailingTransaction()
   *   → call goes directly to method, CDI proxy is bypassed
   *   → @Transactional is silently ignored, no transaction started
   *   → RuntimeException does NOT trigger any rollback
   *   → warehouse may be auto-committed to DB by Panache
   *   → assertNull(found) FAILS
   *
   * Fix: transactionHelper.performFailingTransaction(...)
   *   → call goes through the CDI proxy for TransactionHelper
   *   → @Transactional(REQUIRES_NEW) intercepts the call
   *   → a new transaction is started
   *   → RuntimeException triggers genuine rollback
   *   → nothing is persisted → assertNull(found) PASSES ✅
   */
  @Test
  public void testTransactionRollbackDoesNotPersist() {
    try {
      // FIX: call via injected CDI bean — NOT self-invocation
      transactionHelper.performFailingTransaction(
              warehouseRepository, locationResolver, "ROLLBACK-TEST-001");
    } catch (Exception e) {
      // Expected — RuntimeException from inside the transaction triggers rollback
    }

    Warehouse found = warehouseRepository.findByBusinessUnitCode("ROLLBACK-TEST-001");
    assertNull(found,
            "Warehouse must NOT exist after transaction rollback. " +
                    "If this fails, @Transactional proxy was bypassed (self-invocation bug).");
  }

  // ─────────────────────────────────────────────
  // TC-05: Complex JPQL query by location AND capacity range
  // ─────────────────────────────────────────────

  /**
   * FIX: Changed raw List to List<DbWarehouse> — em.createQuery(..., DbWarehouse.class)
   * returns TypedQuery<DbWarehouse>, so getResultList() returns List<DbWarehouse>.
   * Raw List causes unchecked cast warnings and is not type-safe.
   *
   * Creates 4 warehouses — 3 in AMSTERDAM-001, 1 in ZWOLLE-001.
   * Queries for AMSTERDAM-001 with capacity BETWEEN 40 AND 70.
   * Should find only COMPLEX-2 (cap=50) and COMPLEX-3 (cap=70).
   * COMPLEX-1 (cap=30) is below the range. COMPLEX-4 is a different location.
   */
  @Test
  @Transactional
  public void testComplexQueryByLocationAndCapacity() {
    createWarehouse("COMPLEX-1", "AMSTERDAM-001", 30);
    createWarehouse("COMPLEX-2", "AMSTERDAM-001", 50);
    createWarehouse("COMPLEX-3", "AMSTERDAM-001", 70);
    createWarehouse("COMPLEX-4", "ZWOLLE-001",    40); // cap=40 == maxCap=40 → valid

    // FIX: List<DbWarehouse> — not raw List
    List<DbWarehouse> results = em.createQuery(
                    "SELECT w FROM DbWarehouse w " +
                            "WHERE w.location = :location " +
                            "AND w.capacity BETWEEN :min AND :max",
                    DbWarehouse.class)
            .setParameter("location", "AMSTERDAM-001")
            .setParameter("min", 40)
            .setParameter("max", 70)
            .getResultList();

    // COMPLEX-2 (50) and COMPLEX-3 (70) — COMPLEX-1 (30) is below range
    assertEquals(2, results.size(),
            "Expected COMPLEX-2 (cap=50) and COMPLEX-3 (cap=70) only");

    List<String> codes = results.stream()
            .map(w -> w.businessUnitCode)
            .toList();

    assertTrue(codes.contains("COMPLEX-2"),
            "COMPLEX-2 (capacity=50) should be in results");
    assertTrue(codes.contains("COMPLEX-3"),
            "COMPLEX-3 (capacity=70) should be in results");
    assertFalse(codes.contains("COMPLEX-1"),
            "COMPLEX-1 (capacity=30) should NOT be in results — below range");
  }

  // ─────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────

  private void createWarehouse(String code, String location, int capacity) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = code;
    warehouse.location         = location;
    warehouse.capacity         = capacity;
    warehouse.stock            = 10;
    createWarehouseUseCase.create(warehouse);
  }

  // ─────────────────────────────────────────────
  // Separate CDI bean for rollback test
  // ─────────────────────────────────────────────

  /**
   * WHY this is a separate @ApplicationScoped bean and not a method on the test class:
   *
   * In Quarkus CDI, @Transactional works through a proxy that wraps the bean.
   * The proxy intercepts calls made FROM OUTSIDE the bean instance.
   * When you call `this.someMethod()` inside the same class, the call goes
   * directly to the method — the proxy is never involved — and @Transactional
   * is silently ignored.
   *
   * By extracting to a separate @ApplicationScoped bean and injecting it,
   * all calls to transactionHelper.X() go through the CDI proxy for
   * TransactionHelper, so @Transactional(REQUIRES_NEW) fires correctly.
   */
  @ApplicationScoped
  public static class TransactionHelper {

    @Transactional(TxType.REQUIRES_NEW)
    public void performFailingTransaction(WarehouseRepository repo,
                                          LocationGateway locationGateway,
                                          String code) {
      CreateWarehouseUseCase useCase =
              new CreateWarehouseUseCase(repo, locationGateway);

      Warehouse warehouse = new Warehouse();
      warehouse.businessUnitCode = code;
      warehouse.location         = "TILBURG-001";
      warehouse.capacity         = 30;
      warehouse.stock            = 10;
      useCase.create(warehouse);

      // This exception triggers rollback of the REQUIRES_NEW transaction
      throw new RuntimeException("Simulated failure — triggers TX rollback");
    }
  }
}