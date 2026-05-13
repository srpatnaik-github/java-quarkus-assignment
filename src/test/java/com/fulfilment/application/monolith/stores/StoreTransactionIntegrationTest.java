package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Integration test for store event handling and transaction integrity.
 *
 * Verifies that the legacy system is only notified when store
 * operations complete successfully.
 */
@QuarkusTest
public class StoreTransactionIntegrationTest {

  @InjectMock
  LegacyStoreManagerGateway legacyGateway;

  @Test
  public void testLegacySystemNotNotifiedOnFailedStoreCreation() throws InterruptedException {
    Mockito.reset(legacyGateway);

    String uniqueName = "IntegrationTest_" + System.currentTimeMillis();

    // First create should succeed
    given()
        .contentType("application/json")
        .body("{\"name\": \"" + uniqueName + "\", \"quantityProductsInStock\": 5}")
        .when().post("/store")
        .then()
        .statusCode(201);

    // Allow time for event processing
    Thread.sleep(1000);

    // Legacy system should be notified for the successful creation
    verify(legacyGateway, times(1)).createStoreOnLegacySystem(any(Store.class));

    // Reset for next assertion
    Mockito.reset(legacyGateway);

    // Second create with same name should fail (unique constraint violation)
    given()
        .contentType("application/json")
        .body("{\"name\": \"" + uniqueName + "\", \"quantityProductsInStock\": 10}")
        .when().post("/store")
        .then()
        .statusCode(500);

    // Allow time for any async event processing
    Thread.sleep(1000);

    // Legacy system should NOT be notified for a failed transaction
    verify(legacyGateway, never()).createStoreOnLegacySystem(any(Store.class));
  }

  /**
   * StoreTransactionIntegrationTest only tests create.
   * The PUT /store/{id} path also fires an event — but there is no integration test
   *              verifying the legacy system is notified on a successful update.
   * @throws InterruptedException
   */
  @Test
  public void testLegacySystemNotifiedOnSuccessfulStoreUpdate() throws InterruptedException {
    Mockito.reset(legacyGateway);

    // First create the store
    String uniqueName = "UpdateTest_" + System.currentTimeMillis();
    String responseBody =
            given()
                    .contentType("application/json")
                    .body("{\"name\": \"" + uniqueName + "\", \"quantityProductsInStock\": 5}")
                    .when().post("/store")
                    .then()
                    .statusCode(201)
                    .extract().body().asString();

    Long id = JsonPath.from(responseBody).getLong("id");
    Mockito.reset(legacyGateway);

    // Now update it
    given()
            .contentType("application/json")
            .body("{\"name\": \"" + uniqueName + "_updated\", \"quantityProductsInStock\": 99}")
            .when().put("/store/" + id)
            .then()
            .statusCode(200);

    Thread.sleep(500);

    // Legacy system should be notified of the update
    verify(legacyGateway, times(1)).updateStoreOnLegacySystem(any(Store.class));
  }

  /**
   * Column (length = 40) on name means names > 40 chars will fail at DB level.
   * This should be validated and return a predictable error — important edge case for a unique-constrained field.
   */
  @Test
  public void testCreateStoreWithLongNameFails() {
    String longName = "A".repeat(41); // exceeds 40 char limit

    given()
            .contentType("application/json")
            .body("{\"name\": \"" + longName + "\", \"quantityProductsInStock\": 5}")
            .when().post("/store")
            .then()
            .statusCode(500); // DB constraint violation

    // Legacy must not be notified
    verify(legacyGateway, never()).createStoreOnLegacySystem(any(Store.class));
  }

  /**
   * Edge case — the name field has a unique = true constraint and is the primary
   * identifier. Sending a null/missing name should be rejected before even hitting the DB.
   */
  @Test
  public void testCreateStoreWithInvalidIdReturns422() {
    given()
            .contentType("application/json")
            .body("{\"id\": 1, \"name\": \"ShouldFail\", \"quantityProductsInStock\": 5}")
            .when().post("/store")
            .then()
            .statusCode(422);
  }
}
