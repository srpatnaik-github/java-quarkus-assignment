package com.fulfilment.application.monolith.warehouses.domain;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optimistic locking tests for DbWarehouse using JPA @Version.
 *
 * DbWarehouse already has: @Version public Long version;
 * These tests verify the locking behaviour at the EntityManager level.
 *
 * FIXES APPLIED vs original:
 *
 *   1. testOptimisticLockingPreventsLostUpdates — FUNDAMENTALLY REWRITTEN.
 *      Original bug: em.find() called twice in the same TX returns the SAME
 *      object reference from the JPA first-level (EntityManager) cache.
 *      warehouse1 == warehouse2 in memory — they are literally the same instance.
 *      When TX-B updates and commits (version → 1), and TX-A then merges
 *      warehouse2 (which IS warehouse1), Hibernate does not re-check the DB
 *      version for an entity already managed in the current TX context.
 *      Result: either a silent lost update, or a wrapped RollbackException
 *      (not a bare OptimisticLockException) — assertThrows would never catch it.
 *
 *      FIX: Use em.detach(warehouse2) BEFORE TX-B runs. A detached entity is
 *      no longer in the first-level cache. When TX-A later calls em.merge()
 *      on the detached (now stale) entity, Hibernate fetches the current DB
 *      version, compares it to the stale version on the detached entity,
 *      and throws OptimisticLockException on the version mismatch.
 *
 *   2. setup() — changed location from "ZWOLLE-001" (maxCapacity=40) to
 *      "AMSTERDAM-001" (maxCapacity=100). Using capacity=100 with ZWOLLE-001
 *      violates the location's business rule (though em.persist() bypasses it,
 *      it is inconsistent and misleading).
 *
 *   3. testVersionIncrementsOnUpdate — typo fix (Incrementson → IncrementsOn)
 *      + tightened assertion: assertTrue(version > initial) →
 *        assertEquals(initialVersion + 1, version) for precision.
 *
 *   4. Added TC-03: Initial version is 0 after first persist.
 *   5. Added TC-04: Version does NOT increment on read-only access.
 *   6. Added TC-05: Multiple sequential updates each increment version by 1.
 */
@QuarkusTest
public class WarehouseOptimisticLockingTest {

  @Inject
  EntityManager em;

  private Long warehouseId;

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();

    DbWarehouse warehouse = new DbWarehouse();
    warehouse.businessUnitCode = "OPT-LOCK-001";
    warehouse.location         = "AMSTERDAM-001"; // FIX: was ZWOLLE-001 (maxCap=40 but capacity=100)
    warehouse.capacity         = 100;
    warehouse.stock            = 50;
    warehouse.createdAt        = LocalDateTime.now();

    em.persist(warehouse);
    em.flush();

    warehouseId = warehouse.id;
  }

  // ─────────────────────────────────────────────
  // TC-01: Optimistic locking prevents lost updates
  // ─────────────────────────────────────────────

  /**
   * Scenario:
   *   1. TX-A reads warehouse → version=0 (managed in TX-A's context)
   *   2. em.detach(warehouse2) → removed from TX-A's first-level cache
   *   3. TX-B reads, updates stock=80, commits → DB version=1
   *   4. TX-A merges the detached (stale, version=0) entity
   *   5. Hibernate compares stale version (0) vs DB version (1) → MISMATCH
   *   6. OptimisticLockException thrown ✅
   *
   * WHY detach() is required:
   *   em.find() twice in the same TX returns the SAME object reference from
   *   the JPA first-level cache. Without detach(), warehouse2 IS warehouse1 —
   *   same Java instance. Merging a managed entity that is already in the
   *   context does not trigger a DB version check — Hibernate just updates
   *   what it's already tracking. detach() forces warehouse2 out of the
   *   context so that merge() re-reads and compares the DB version.
   */
  @Test
  @Transactional
  public void testOptimisticLockingPreventsLostUpdates() {
    // Step 1: Load entity into TX-A context
    DbWarehouse warehouse2 = em.find(DbWarehouse.class, warehouseId);
    Long versionBeforeTxB = warehouse2.version;

    // Step 2: DETACH — removes from first-level cache so merge() will
    //         re-fetch and compare DB version on the next merge call
    em.detach(warehouse2);

    // Step 3: TX-B updates and commits → DB version increments
    updateWarehouseInSeparateTransaction(warehouseId, 80);

    // Step 4 & 5: Merge the stale detached entity — version mismatch → exception
    warehouse2.stock = 90;  // modify the detached (stale) entity

    assertThrows(OptimisticLockException.class, () -> {
      em.merge(warehouse2);
      em.flush();
    }, "Merging a stale detached entity should throw OptimisticLockException. " +
            "Version before TX-B: " + versionBeforeTxB);
  }

  // ─────────────────────────────────────────────
  // TC-02: Version increments by exactly 1 on each update
  // ─────────────────────────────────────────────

  /**
   * FIX: Renamed from testVersionIncrementsonUpdate (typo in 'on').
   * FIX: Tightened assertion from assertTrue(version > initial)
   *      to assertEquals(initialVersion + 1, version) — documents the
   *      exact increment step, not just "something changed".
   */
  @Test
  @Transactional
  public void testVersionIncrementsOnUpdate() {
    DbWarehouse warehouse = em.find(DbWarehouse.class, warehouseId);
    Long initialVersion = warehouse.version;

    warehouse.stock = 60;
    em.merge(warehouse);
    em.flush();

    // FIX: precise assertion — version must increment by exactly 1
    assertEquals(initialVersion + 1, warehouse.version,
            "Version must increment by exactly 1 after each update");
  }

  // ─────────────────────────────────────────────
  // TC-03 (NEW): Initial version is 0 after first persist
  // ─────────────────────────────────────────────

  /**
   * JPA spec: @Version Long is initialised to 0 on first persist.
   * This test locks in that baseline behaviour for this application.
   */
  @Test
  @Transactional
  public void testInitialVersionIsZeroAfterPersist() {
    DbWarehouse warehouse = em.find(DbWarehouse.class, warehouseId);

    assertNotNull(warehouse.version,
            "version field must not be null after persist");
    assertEquals(0L, warehouse.version,
            "version must start at 0 after first persist");
  }

  // ─────────────────────────────────────────────
  // TC-04 (NEW): Version does NOT increment on read-only access
  // ─────────────────────────────────────────────

  /**
   * Reading an entity without modifying it must not change the version.
   * This confirms @Version only reacts to actual data mutations.
   */
  @Test
  @Transactional
  public void testVersionDoesNotIncrementOnReadOnly() {
    DbWarehouse warehouse = em.find(DbWarehouse.class, warehouseId);
    Long versionAfterRead = warehouse.version;

    // Re-read without any modification
    DbWarehouse readAgain = em.find(DbWarehouse.class, warehouseId);

    assertEquals(versionAfterRead, readAgain.version,
            "Version must not change on a read-only access — no mutation, no increment");
  }

  // ─────────────────────────────────────────────
  // TC-05 (NEW): Multiple sequential updates each increment version
  // ─────────────────────────────────────────────

  /**
   * Three sequential updates must each increment version by 1.
   * Final version must equal initialVersion + 3.
   */
  @Test
  public void testVersionIncrementsOnEachSequentialUpdate() {
    Long v0 = getVersionInNewTransaction(warehouseId);

    updateWarehouseInSeparateTransaction(warehouseId, 55);
    Long v1 = getVersionInNewTransaction(warehouseId);
    assertEquals(v0 + 1, v1, "Version after 1st update must be v0 + 1");

    updateWarehouseInSeparateTransaction(warehouseId, 60);
    Long v2 = getVersionInNewTransaction(warehouseId);
    assertEquals(v0 + 2, v2, "Version after 2nd update must be v0 + 2");

    updateWarehouseInSeparateTransaction(warehouseId, 65);
    Long v3 = getVersionInNewTransaction(warehouseId);
    assertEquals(v0 + 3, v3, "Version after 3rd update must be v0 + 3");
  }

  // ─────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────

  @Transactional(TxType.REQUIRES_NEW)
  void updateWarehouseInSeparateTransaction(Long id, int newStock) {
    DbWarehouse warehouse = em.find(DbWarehouse.class, id);
    warehouse.stock = newStock;
    em.merge(warehouse);
    em.flush();
  }

  @Transactional(TxType.REQUIRES_NEW)
  Long getVersionInNewTransaction(Long id) {
    DbWarehouse warehouse = em.find(DbWarehouse.class, id);
    return warehouse.version;
  }
}