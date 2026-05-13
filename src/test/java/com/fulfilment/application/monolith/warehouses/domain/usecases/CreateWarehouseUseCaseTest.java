package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CreateWarehouseUseCase.
 *
 * The use case enforces 4 validations in order:
 *   1. businessUnitCode must be unique       → "already exists"
 *   2. location must exist                   → "is not valid"
 *   3. capacity ≤ location.maxCapacity()     → "exceeds location max capacity"
 *   4. stock ≤ capacity                      → "exceeds warehouse capacity"
 *
 * After all validations pass, createdAt is auto-set to LocalDateTime.now().
 *
 * Error message fragments are taken DIRECTLY from CreateWarehouseUseCase.java
 * source to ensure they never drift out of sync with the implementation.
 */
@QuarkusTest
public class CreateWarehouseUseCaseTest {

    @Inject
    WarehouseRepository warehouseRepository;  // implements WarehouseStore

    @Inject
    LocationGateway locationGateway;          // implements LocationResolver

    @Inject
    EntityManager em;

    private CreateWarehouseUseCase createWarehouseUseCase;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean slate before every test — prevents duplicate code failures
        // on repeated test runs and isolates each test completely
        em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
        createWarehouseUseCase = new CreateWarehouseUseCase(warehouseRepository, locationGateway);
    }

    // ─────────────────────────────────────────────────────────────
    // HAPPY PATH
    // ─────────────────────────────────────────────────────────────

    /**
     * TC-01: Successfully creates a warehouse with valid data.
     * Verifies all fields are persisted correctly AND createdAt is auto-set.
     */
    @Test
    @Transactional
    public void testCreateWarehouseWithValidDataSucceeds() {
        Warehouse warehouse = buildWarehouse("CREATE-001", "AMSTERDAM-001", 50, 20);

        createWarehouseUseCase.create(warehouse);

        Warehouse found = warehouseRepository.findByBusinessUnitCode("CREATE-001");
        assertNotNull(found, "Warehouse should be persisted after creation");
        assertEquals("CREATE-001",    found.businessUnitCode);
        assertEquals("AMSTERDAM-001", found.location);
        assertEquals(50,              found.capacity);
        assertEquals(20,              found.stock);
        assertNotNull(found.createdAt,
                "createdAt must be automatically set by the use case");
        assertNull(found.archivedAt,
                "archivedAt must be null for a newly created warehouse");
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATION 1: businessUnitCode uniqueness
    // ─────────────────────────────────────────────────────────────

    /**
     * TC-02: Cannot create two warehouses with the same businessUnitCode.
     * Error: "Warehouse with business unit code '...' already exists"
     */
    @Test
    @Transactional
    public void testCreateFailsWhenBusinessUnitCodeAlreadyExists() {
        // Create first warehouse successfully
        createWarehouseUseCase.create(buildWarehouse("DUP-001", "AMSTERDAM-001", 50, 10));

        // Attempt to create second with same businessUnitCode
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("DUP-001", "ZWOLLE-001", 30, 5)),
                "Should reject duplicate businessUnitCode"
        );
        assertTrue(ex.getMessage().contains("already exists"),
                "Error message should contain 'already exists'. Got: " + ex.getMessage());
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATION 2: Location must exist
    // ─────────────────────────────────────────────────────────────

    /**
     * TC-03: Cannot create warehouse with an unknown location.
     * Error: "Location '...' is not valid"
     */
    @Test
    public void testCreateFailsWhenLocationDoesNotExist() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("LOC-001", "MARS-001", 30, 10)),
                "Should reject unknown location"
        );
        assertTrue(ex.getMessage().contains("is not valid"),
                "Error message should contain 'is not valid'. Got: " + ex.getMessage());
    }

    /**
     * TC-04: Cannot create warehouse with an empty location string.
     * Error: "Location '' is not valid"
     */
    @Test
    public void testCreateFailsWhenLocationIsEmpty() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("LOC-002", "", 30, 10)),
                "Should reject empty location"
        );
        assertTrue(ex.getMessage().contains("is not valid"),
                "Error message should contain 'is not valid'. Got: " + ex.getMessage());
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATION 3: capacity ≤ location.maxCapacity()
    // ─────────────────────────────────────────────────────────────

    /**
     * TC-05: Cannot create warehouse when capacity exceeds location max.
     * ZWOLLE-001 maxCapacity = 40. Trying capacity = 41.
     * Error: "...exceeds location max capacity..."
     */
    @Test
    public void testCreateFailsWhenCapacityExceedsLocationMax() {
        // ZWOLLE-001 maxCapacity=40, trying capacity=41 (one over the limit)
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("CAP-001", "ZWOLLE-001", 41, 10)),
                "Should reject capacity exceeding location max (40 for ZWOLLE-001)"
        );
        assertTrue(ex.getMessage().contains("exceeds location max capacity"),
                "Error message should contain 'exceeds location max capacity'. Got: " + ex.getMessage());
    }

    /**
     * TC-06: Boundary — capacity exactly AT location max must be accepted.
     * ZWOLLE-001 maxCapacity = 40. capacity = 40 is valid (rule is strictly >).
     */
    @Test
    @Transactional
    public void testCreateSucceedsWhenCapacityEqualsLocationMax() {
        // capacity = 40, location max = 40 → exactly at boundary → valid
        assertDoesNotThrow(
                () -> createWarehouseUseCase.create(buildWarehouse("CAP-002", "ZWOLLE-001", 40, 10)),
                "Capacity exactly equal to location max (40) should be accepted"
        );
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATION 4: stock ≤ capacity
    // ─────────────────────────────────────────────────────────────

    /**
     * TC-07: Cannot create warehouse when stock exceeds capacity.
     * Error: "...exceeds warehouse capacity..."
     */
    @Test
    public void testCreateFailsWhenStockExceedsCapacity() {
        // capacity=30, stock=31 (one over)
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("STK-001", "AMSTERDAM-001", 30, 31)),
                "Should reject stock exceeding capacity"
        );
        assertTrue(ex.getMessage().contains("exceeds warehouse capacity"),
                "Error message should contain 'exceeds warehouse capacity'. Got: " + ex.getMessage());
    }

    /**
     * TC-08: Boundary — stock exactly equal to capacity must be accepted.
     * Rule is strictly > so stock == capacity is valid.
     */
    @Test
    @Transactional
    public void testCreateSucceedsWhenStockEqualsCapacity() {
        // capacity=30, stock=30 → exactly at boundary → valid
        assertDoesNotThrow(
                () -> createWarehouseUseCase.create(buildWarehouse("STK-002", "AMSTERDAM-001", 30, 30)),
                "Stock exactly equal to capacity should be accepted"
        );
    }

    /**
     * TC-09: Boundary — zero stock must be accepted.
     * A warehouse can be created empty.
     */
    @Test
    @Transactional
    public void testCreateSucceedsWithZeroStock() {
        assertDoesNotThrow(
                () -> createWarehouseUseCase.create(buildWarehouse("STK-003", "AMSTERDAM-001", 50, 0)),
                "Zero stock should be accepted — warehouse can start empty"
        );
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATION ORDER: capacity checked BEFORE stock
    // ─────────────────────────────────────────────────────────────

    /**
     * TC-10: When BOTH capacity > location max AND stock > capacity,
     * the capacity error (Validation 3) must fire before stock error (Validation 4).
     * This tests that the validation order in the implementation is correct.
     *
     * ZWOLLE-001 maxCapacity=40. capacity=99 (>40), stock=100 (>capacity).
     * Expected: "exceeds location max capacity" fires — NOT "exceeds warehouse capacity".
     */
    @Test
    public void testCapacityValidationFiresBeforeStockValidation() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("ORD-001", "ZWOLLE-001", 99, 100))
        );
        assertTrue(ex.getMessage().contains("exceeds location max capacity"),
                "Validation 3 (capacity > location max) should fire before Validation 4 (stock > capacity). " +
                        "Got: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("exceeds warehouse capacity"),
                "Stock error should NOT appear when capacity is already invalid. " +
                        "Got: " + ex.getMessage());
    }

    /**
     * TC-11: When BOTH location is invalid AND capacity > location max,
     * the location error (Validation 2) must fire before capacity error (Validation 3).
     */
    @Test
    public void testLocationValidationFiresBeforeCapacityValidation() {
        // Invalid location + capacity that would also be invalid
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> createWarehouseUseCase.create(buildWarehouse("ORD-002", "MARS-001", 9999, 10))
        );
        assertTrue(ex.getMessage().contains("is not valid"),
                "Validation 2 (location invalid) should fire before Validation 3 (capacity). " +
                        "Got: " + ex.getMessage());
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private Warehouse buildWarehouse(String code, String location, int capacity, int stock) {
        Warehouse w = new Warehouse();
        w.businessUnitCode = code;
        w.location         = location;
        w.capacity         = capacity;
        w.stock            = stock;
        return w;
    }
}