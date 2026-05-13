package com.fulfilment.application.monolith.location;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocationGateway.
 *
 * NOTE: LocationGateway is a plain @ApplicationScoped bean with no external
 * dependencies — it can be instantiated directly without @QuarkusTest,
 * making these fast plain unit tests (no container startup needed).
 *
 * FIXES:
 *   1. Original test body was entirely commented out — passes without any testing.
 *   2. Original used wrong field: location.identification → correct is location.identifier()
 *   3. Added all 8 known locations verified with all 3 record fields:
 *      identifier, maxNumberOfWarehouses, maxCapacity.
 *   4. Added negative cases: unknown location, null input, empty string.
 *
 *   Abbreviations used - TC(Test case)
 */
public class LocationGatewayTest {

  private LocationGateway locationGateway;

  @BeforeEach
  public void setup() {
    locationGateway = new LocationGateway();
  }

  // ─────────────────────────────────────────────
  // TC-01: Original test — now actually implemented
  // ─────────────────────────────────────────────

  /**
   * The original commented-out test — properly implemented.
   * FIX: was location.identification → correct field is location.identifier()
   */
  @Test
  public void testWhenResolveExistingLocationShouldReturn() {
    Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

    assertNotNull(location);
    assertEquals("ZWOLLE-001", location.identifier()); // FIX: was .identification
  }

  // ─────────────────────────────────────────────
  // TC-02: All 8 known locations resolve to non-null
  // ─────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {
          "ZWOLLE-001", "ZWOLLE-002",
          "AMSTERDAM-001", "AMSTERDAM-002",
          "TILBURG-001", "HELMOND-001",
          "EINDHOVEN-001", "VETSBY-001"
  })
  public void testAllKnownLocationsResolveSuccessfully(String identifier) {
    Location location = locationGateway.resolveByIdentifier(identifier);

    assertNotNull(location,
            "Expected location '" + identifier + "' to be found but got null");
    assertEquals(identifier, location.identifier());
  }

  // ─────────────────────────────────────────────
  // TC-03: All 3 fields verified for every location
  // ─────────────────────────────────────────────

  /**
   * Contract test — verifies all 3 record fields for all 8 locations.
   * If LocationGateway data changes, this test fails and alerts the team.
   * Format: identifier, maxNumberOfWarehouses, maxCapacity
   */
  @ParameterizedTest
  @CsvSource({
          "ZWOLLE-001,    1,  40",
          "ZWOLLE-002,    2,  50",
          "AMSTERDAM-001, 5, 100",
          "AMSTERDAM-002, 3,  75",
          "TILBURG-001,   1,  40",
          "HELMOND-001,   1,  45",
          "EINDHOVEN-001, 2,  70",
          "VETSBY-001,    1,  90"
  })
  public void testLocationDataIsCorrect(String identifier,
                                        int maxWarehouses,
                                        int maxCapacity) {
    Location location = locationGateway.resolveByIdentifier(identifier);

    assertNotNull(location);
    assertEquals(identifier,    location.identifier());
    assertEquals(maxWarehouses, location.maxNumberOfWarehouses());
    assertEquals(maxCapacity,   location.maxCapacity());
  }

  // ─────────────────────────────────────────────
  // TC-04: Unknown location returns null
  // ─────────────────────────────────────────────

  @Test
  public void testUnknownLocationReturnsNull() {
    Location location = locationGateway.resolveByIdentifier("MARS-001");

    assertNull(location, "Non-existent location should return null, not throw");
  }

  // ─────────────────────────────────────────────
  // TC-05: Null input returns null (no NullPointerException)
  // ─────────────────────────────────────────────

  /**
   * String.equals() safely handles null — "ZWOLLE-001".equals(null) returns false.
   * So LocationGateway.resolveByIdentifier(null) returns null safely.
   * This test documents and locks in that safe behaviour.
   */
  @Test
  public void testNullIdentifierReturnsNull() {
    Location location = locationGateway.resolveByIdentifier(null);

    assertNull(location, "Null identifier should return null without throwing NPE");
  }

  // ─────────────────────────────────────────────
  // TC-06: Empty string returns null
  // ─────────────────────────────────────────────

  @Test
  public void testEmptyStringIdentifierReturnsNull() {
    Location location = locationGateway.resolveByIdentifier("");

    assertNull(location, "Empty string should return null — it is not a valid location");
  }
}