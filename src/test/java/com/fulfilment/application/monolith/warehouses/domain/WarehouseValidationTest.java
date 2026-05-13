package com.fulfilment.application.monolith.warehouses.domain;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.usecases.CreateWarehouseUseCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized validation tests for CreateWarehouseUseCase.
 *
 * Demonstrates systematic edge case testing using @ParameterizedTest + @MethodSource.
 * Each scenario is defined as data — one test method covers all cases for that rule.
 *
 * FIXES APPLIED vs original:
 *   1. Added DB cleanup to @BeforeEach — without it, testDuplicateBusinessUnitCode
 *      fails on the second run because DUP-CODE-001/002/003 already exist in the DB.
 *      The first create() throws "already exists" instead of succeeding.
 *
 *   2. testInvalidCapacityScenarios — added @Transactional + UUID businessUnitCode.
 *      Original used System.currentTimeMillis() which can collide across iterations
 *      on fast machines, causing duplicate-code error instead of capacity error.
 *
 *   3. testInvalidLocationScenarios — same fix as above (UUID + @Transactional).
 *
 *   4. Fixed raw Stream return types → Stream<Arguments> to eliminate unchecked warnings.
 *
 *   5. Added valid boundary scenarios to testValidBoundaryScenarios — a validation
 *      test should confirm valid values pass, not only that invalid values fail.
 */
@QuarkusTest
public class WarehouseValidationTest {

  @Inject
  WarehouseRepository warehouseRepository;

  @Inject
  LocationGateway locationResolver;

  @Inject
  EntityManager em;

  private CreateWarehouseUseCase createWarehouseUseCase;

  /**
   * FIX: Added DELETE FROM DbWarehouse.
   * Without this, duplicate code tests fail on re-runs because previously
   * created warehouses (DUP-CODE-001 etc.) still exist in the DB.
   */
  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
    createWarehouseUseCase = new CreateWarehouseUseCase(warehouseRepository, locationResolver);
  }

  // ─────────────────────────────────────────────────────────────
  // Invalid capacity / stock scenarios
  // ─────────────────────────────────────────────────────────────

  /**
   * FIX 1: Added @Transactional so each iteration is properly isolated.
   * FIX 2: businessUnitCode now uses UUID — System.currentTimeMillis() can
   *         collide on fast machines (two iterations in same millisecond),
   *         causing Validation 1 (duplicate code) to fire instead of
   *         the intended capacity/location validation.
   */
  @ParameterizedTest(name = "[{index}] location={2}, capacity={0}, stock={1} → expects \"{3}\"")
  @MethodSource("invalidCapacityScenarios")
  @Transactional
  public void testInvalidCapacityScenarios(int capacity, int stock,
                                           String location, String expectedError) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "TEST-" + UUID.randomUUID(); // FIX: was currentTimeMillis()
    warehouse.location         = location;
    warehouse.capacity         = capacity;
    warehouse.stock            = stock;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> createWarehouseUseCase.create(warehouse));

    assertTrue(ex.getMessage().contains(expectedError),
            "Expected error to contain '" + expectedError + "' but got: " + ex.getMessage());
  }

  /**
   * FIX: Return type changed from raw Stream to Stream<Arguments>.
   *
   * Scenarios:
   *   - capacity > location maxCapacity → "exceeds location max capacity"
   *   - stock > capacity                → "exceeds warehouse capacity"
   *
   * Location facts used:
   *   ZWOLLE-001:    maxCapacity=40
   *   AMSTERDAM-001: maxCapacity=100
   */
  private static Stream<Arguments> invalidCapacityScenarios() {
    return Stream.of(
            // capacity, stock, location, expectedError
            Arguments.of(41,  10, "ZWOLLE-001",    "exceeds location max capacity"), // 41 > maxCap 40
            Arguments.of(30,  50, "ZWOLLE-001",    "exceeds warehouse capacity"),    // stock 50 > cap 30
            Arguments.of(101, 10, "AMSTERDAM-001", "exceeds location max capacity"), // 101 > maxCap 100
            Arguments.of(50,  60, "AMSTERDAM-001", "exceeds warehouse capacity")     // stock 60 > cap 50
    );
  }

  // ─────────────────────────────────────────────────────────────
  // Invalid location scenarios
  // ─────────────────────────────────────────────────────────────

  /**
   * FIX: Added @Transactional + UUID — same collision risk as capacity test.
   */
  @ParameterizedTest(name = "[{index}] location=\"{0}\" → expects \"{1}\"")
  @MethodSource("invalidLocationScenarios")
  @Transactional
  public void testInvalidLocationScenarios(String location, String expectedError) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "TEST-LOC-" + UUID.randomUUID(); // FIX: was currentTimeMillis()
    warehouse.location         = location;
    warehouse.capacity         = 10;
    warehouse.stock            = 5;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> createWarehouseUseCase.create(warehouse));

    assertTrue(ex.getMessage().contains(expectedError),
            "Expected error to contain '" + expectedError + "' but got: " + ex.getMessage());
  }

  // FIX: Stream<Arguments> instead of raw Stream
  private static Stream<Arguments> invalidLocationScenarios() {
    return Stream.of(
            Arguments.of("INVALID-LOC",    "is not valid"),
            Arguments.of("NONEXISTENT-001","is not valid"),
            Arguments.of("",               "is not valid")  // empty string
    );
  }

  // ─────────────────────────────────────────────────────────────
  // Duplicate businessUnitCode scenarios
  // ─────────────────────────────────────────────────────────────

  /**
   * FIX: @BeforeEach now clears DB so DUP-CODE-001/002/003 don't exist
   * from a previous run, which would cause warehouse1.create() to throw
   * "already exists" instead of succeeding as expected.
   */
  @ParameterizedTest(name = "[{index}] duplicate code \"{0}\"")
  @MethodSource("duplicateBusinessCodeScenarios")
  @Transactional
  public void testDuplicateBusinessUnitCode(String code) {
    // First create — must succeed
    Warehouse warehouse1 = new Warehouse();
    warehouse1.businessUnitCode = code;
    warehouse1.location         = "ZWOLLE-001";
    warehouse1.capacity         = 10;
    warehouse1.stock            = 5;
    assertDoesNotThrow(
            () -> createWarehouseUseCase.create(warehouse1),
            "First creation of '" + code + "' should succeed"
    );

    // Second create with same code — must fail
    Warehouse warehouse2 = new Warehouse();
    warehouse2.businessUnitCode = code;  // deliberate duplicate
    warehouse2.location         = "AMSTERDAM-001";
    warehouse2.capacity         = 20;
    warehouse2.stock            = 10;

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> createWarehouseUseCase.create(warehouse2),
            "Second creation of '" + code + "' must throw"
    );

    assertTrue(ex.getMessage().contains("already exists"),
            "Error should contain 'already exists'. Got: " + ex.getMessage());
  }

  // FIX: Stream<Arguments> instead of raw Stream
  private static Stream<Arguments> duplicateBusinessCodeScenarios() {
    return Stream.of(
            Arguments.of("DUP-CODE-001"),
            Arguments.of("DUP-CODE-002"),
            Arguments.of("DUP-CODE-003")
    );
  }

  // ─────────────────────────────────────────────────────────────
  // NEW: Valid boundary scenarios — confirm valid values ARE accepted
  // ─────────────────────────────────────────────────────────────

  /**
   * A validation test should not only confirm bad values fail —
   * it must also confirm that valid boundary values PASS.
   * This ensures the validation rules use > (strictly greater) not >= .
   *
   * Boundary cases:
   *   - capacity == location.maxCapacity() → should succeed (not exceeds)
   *   - stock == capacity                  → should succeed (not exceeds)
   *   - stock == 0                         → should succeed (empty warehouse)
   */
  @ParameterizedTest(name = "[{index}] valid boundary: location={0}, capacity={1}, stock={2}")
  @MethodSource("validBoundaryScenarios")
  @Transactional
  public void testValidBoundaryScenarios(String location, int capacity, int stock) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "BOUNDARY-" + UUID.randomUUID();
    warehouse.location         = location;
    warehouse.capacity         = capacity;
    warehouse.stock            = stock;

    assertDoesNotThrow(
            () -> createWarehouseUseCase.create(warehouse),
            "Valid boundary values should be accepted: location=" + location +
                    ", capacity=" + capacity + ", stock=" + stock
    );
  }

  private static Stream<Arguments> validBoundaryScenarios() {
    return Stream.of(
            // location,        capacity, stock
            // capacity exactly at location max → valid
            Arguments.of("ZWOLLE-001",    40, 20),  // maxCapacity=40, capacity=40 → OK
            Arguments.of("AMSTERDAM-001", 100, 50), // maxCapacity=100, capacity=100 → OK
            // stock exactly equal to capacity → valid
            Arguments.of("AMSTERDAM-001", 50, 50),  // stock==capacity → OK
            // zero stock → valid (empty warehouse)
            Arguments.of("AMSTERDAM-001", 50, 0)
    );
  }
}