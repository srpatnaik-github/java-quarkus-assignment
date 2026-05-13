package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ArchiveWarehouseUseCase.
 *
 * The use case enforces 2 validations in order:
 *   1. Warehouse must exist          → "does not exist"
 *   2. Warehouse must not be archived → "is already archived"
 *
 * After validations pass → existing.archivedAt = LocalDateTime.now() → update().
 *
 * FIXES APPLIED vs original:
 *   - TC-01: Added timestamp precision check (archivedAt is recent, not just non-null)
 *   - TC-02: Error message fragment updated to match exact source: "does not exist"
 *   - TC-03: Error message fragment updated to match exact source: "is already archived"
 *   - TC-04 (NEW): Verify archive preserves all other fields unchanged
 *   - TC-05 (NEW): Archive works when only businessUnitCode is set on input object
 *   - TC-06 (concurrent): Logic is correct — the real fix is adding @Version to
 *             DbWarehouse. Added clarifying comment explaining the failure scenario.
 */
@QuarkusTest
public class ArchiveWarehouseUseCaseTest {

  @Inject
  WarehouseRepository warehouseRepository;

  @Inject
  ArchiveWarehouseUseCase archiveWarehouseUseCase;

  @Inject
  EntityManager em;

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
  }

  // ─────────────────────────────────────────────
  // TC-01: Happy path — archivedAt is set correctly
  // ─────────────────────────────────────────────

  /**
   * Archives an existing warehouse and verifies:
   * - archivedAt is not null
   * - archivedAt is a recent timestamp (within last 2 seconds) — not just non-null
   */
  @Test
  @Transactional
  public void testArchiveWarehouseSuccessfully() {
    createWarehouse("ARCHIVE-TEST-001", "AMSTERDAM-001", 100, 50);

    LocalDateTime before = LocalDateTime.now().minusSeconds(1);

    Warehouse input = new Warehouse();
    input.businessUnitCode = "ARCHIVE-TEST-001";
    archiveWarehouseUseCase.archive(input);

    LocalDateTime after = LocalDateTime.now().plusSeconds(1);

    Warehouse archived = warehouseRepository.findByBusinessUnitCode("ARCHIVE-TEST-001");
    assertNotNull(archived);
    assertNotNull(archived.archivedAt, "archivedAt must be set after archiving");
    // FIX: also verify archivedAt is a recent timestamp — not just non-null
    assertTrue(archived.archivedAt.isAfter(before),
            "archivedAt should be after test started");
    assertTrue(archived.archivedAt.isBefore(after),
            "archivedAt should be before test ended");
  }

  // ─────────────────────────────────────────────
  // TC-02: Cannot archive non-existent warehouse
  // ─────────────────────────────────────────────

  /**
   * Exact error message from source: "...does not exist"
   */
  @Test
  @Transactional
  public void testCannotArchiveNonExistentWarehouse() {
    Warehouse input = new Warehouse();
    input.businessUnitCode = "NON-EXISTENT";

    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> archiveWarehouseUseCase.archive(input)
    );
    assertTrue(ex.getMessage().contains("does not exist"),
            "Error should contain 'does not exist'. Got: " + ex.getMessage());
  }

  // ─────────────────────────────────────────────
  // TC-03: Cannot archive already-archived warehouse
  // ─────────────────────────────────────────────

  /**
   * Exact error message from source: "...is already archived"
   */
  @Test
  @Transactional
  public void testCannotArchiveAlreadyArchivedWarehouse() {
    createWarehouse("ARCHIVE-TEST-002", "ZWOLLE-001", 40, 10);

    // Archive once — succeeds
    Warehouse input = new Warehouse();
    input.businessUnitCode = "ARCHIVE-TEST-002";
    archiveWarehouseUseCase.archive(input);

    // Archive again — must fail
    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> archiveWarehouseUseCase.archive(input)
    );
    assertTrue(ex.getMessage().contains("is already archived"),
            "Error should contain 'is already archived'. Got: " + ex.getMessage());
  }

  // ─────────────────────────────────────────────
  // TC-04 (NEW): Archive only sets archivedAt — all other fields preserved
  // ─────────────────────────────────────────────

  /**
   * The implementation fetches the existing entity and only sets archivedAt.
   * This test verifies location, capacity, stock, and createdAt are untouched.
   */
  @Test
  @Transactional
  public void testArchivePreservesAllOtherFields() {
    LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
    createWarehouseWithCreatedAt("ARCHIVE-TEST-003", "AMSTERDAM-001", 80, 40, createdAt);

    Warehouse input = new Warehouse();
    input.businessUnitCode = "ARCHIVE-TEST-003";
    archiveWarehouseUseCase.archive(input);

    Warehouse archived = warehouseRepository.findByBusinessUnitCode("ARCHIVE-TEST-003");
    assertNotNull(archived.archivedAt, "archivedAt should be set");
    // Verify nothing else changed
    assertEquals("AMSTERDAM-001", archived.location,
            "location must not be changed by archive");
    assertEquals(80, archived.capacity,
            "capacity must not be changed by archive");
    assertEquals(40, archived.stock,
            "stock must not be changed by archive");
    assertEquals(createdAt.withNano(0), archived.createdAt.withNano(0),
            "createdAt must not be changed by archive");
  }

  // ─────────────────────────────────────────────
  // TC-05 (NEW): Only businessUnitCode needed on input object
  // ─────────────────────────────────────────────

  /**
   * The implementation only reads warehouse.businessUnitCode from the input —
   * it fetches the real entity internally using warehouseStore.findByBusinessUnitCode().
   * This test confirms that a minimal Warehouse object (only businessUnitCode set)
   * is sufficient — other fields on the input are irrelevant.
   */
  @Test
  @Transactional
  public void testArchiveOnlyRequiresBusinessUnitCodeOnInputObject() {
    createWarehouse("ARCHIVE-TEST-004", "AMSTERDAM-001", 50, 20);

    // Minimal input — only businessUnitCode set, all other fields at defaults
    Warehouse minimalInput = new Warehouse();
    minimalInput.businessUnitCode = "ARCHIVE-TEST-004";
    // location, capacity, stock intentionally NOT set

    assertDoesNotThrow(
            () -> archiveWarehouseUseCase.archive(minimalInput),
            "Archive should work with only businessUnitCode set on input object"
    );

    Warehouse archived = warehouseRepository.findByBusinessUnitCode("ARCHIVE-TEST-004");
    assertNotNull(archived.archivedAt, "Warehouse should be archived");
  }

  // ─────────────────────────────────────────────
  // TC-06: Concurrent archive + stock update — no lost update
  // ─────────────────────────────────────────────

  /**
   * Two threads simultaneously modify the same warehouse:
   *   Thread 1: archives it (sets archivedAt)
   *   Thread 2: updates stock to 75
   *
   * REQUIRES: @Version field on DbWarehouse for optimistic locking.
   * Without @Version, both threads commit independently — one silently
   * overwrites the other (lost update). The build failure we saw was:
   *   archivedAt=2026-05-12T11:01:00..., stock=50
   * meaning Thread 1's archive committed but Thread 2's stock update was
   * silently lost, and NO exception was thrown → bothChangesApplied=false,
   * exceptionCaught=false → assertTrue(false || false) → FAIL.
   *
   * With @Version on DbWarehouse:
   *   - Thread 1 commits → version increments
   *   - Thread 2 detects version mismatch → OptimisticLockException
   *   - exceptionCaught=true → assertTrue(false || true) → PASS ✅
   */
  @Test
  public void testConcurrentArchiveAndStockUpdateCausesOptimisticLockException()
          throws InterruptedException {

    String code = createWarehouseInNewTransaction("CONCURRENT-ARCHIVE-001", "AMSTERDAM-001");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch  = new CountDownLatch(1);
    CountDownLatch finishLatch = new CountDownLatch(2);

    AtomicBoolean archiveSuccess  = new AtomicBoolean(false);
    AtomicBoolean updateSuccess   = new AtomicBoolean(false);
    AtomicBoolean exceptionCaught = new AtomicBoolean(false);

    // Thread 1: Archive warehouse
    executor.submit(() -> {
      try {
        startLatch.await();
        archiveWarehouseInNewTransaction(code);
        archiveSuccess.set(true);
      } catch (Exception e) {
        exceptionCaught.set(true);
      } finally {
        finishLatch.countDown();
      }
    });

    // Thread 2: Update stock concurrently
    executor.submit(() -> {
      try {
        startLatch.await();
        updateStockInNewTransaction(code, 75);
        updateSuccess.set(true);
      } catch (Exception e) {
        exceptionCaught.set(true);
      } finally {
        finishLatch.countDown();
      }
    });

    startLatch.countDown();
    finishLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    Warehouse finalWarehouse = warehouseRepository.findByBusinessUnitCode(code);

    boolean bothChangesApplied =
            finalWarehouse.archivedAt != null && finalWarehouse.stock == 75;

    assertTrue(bothChangesApplied || exceptionCaught.get(),
            "Expected either both changes applied OR an exception thrown. " +
                    "A silent lost update occurred — did you add @Version to DbWarehouse? " +
                    "Final state: archivedAt=" + finalWarehouse.archivedAt +
                    ", stock=" + finalWarehouse.stock);

    if (!exceptionCaught.get()) {
      assertNotNull(finalWarehouse.archivedAt, "archivedAt should be set");
      assertEquals(75, finalWarehouse.stock, "Stock should be updated to 75");
    }
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
                                    int capacity, int stock,
                                    LocalDateTime createdAt) {
    Warehouse w = new Warehouse();
    w.businessUnitCode = code;
    w.location         = location;
    w.capacity         = capacity;
    w.stock            = stock;
    w.createdAt        = createdAt;
    warehouseRepository.create(w);
  }

  @Transactional(TxType.REQUIRES_NEW)
  String createWarehouseInNewTransaction(String code, String location) {
    createWarehouse(code, location, 100, 50);
    return code;
  }

  @Transactional(TxType.REQUIRES_NEW)
  void archiveWarehouseInNewTransaction(String code) {
    Warehouse input = new Warehouse();
    input.businessUnitCode = code;
    archiveWarehouseUseCase.archive(input);
  }

  @Transactional(TxType.REQUIRES_NEW)
  void updateStockInNewTransaction(String code, int newStock) {
    Warehouse w = warehouseRepository.findByBusinessUnitCode(code);
    w.stock = newStock;
    warehouseRepository.update(w);
  }
}