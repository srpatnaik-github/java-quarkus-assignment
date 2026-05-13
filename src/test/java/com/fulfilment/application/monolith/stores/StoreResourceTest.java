package com.fulfilment.application.monolith.stores;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
public class StoreResourceTest {

    // ─── GET /store ────────────────────────────────────────────────────────────

    @Test
    public void testListAllStores_returns200WithSortedList() {
        // Create at least one store to ensure data exists
        given()
                .contentType("application/json")
                .body("{\"name\": \"BESTÅ-TEST\", \"quantityProductsInStock\": 5}")
                .when().post("/store")
                .then().statusCode(201);

        given()
                .when().get("/store")
                .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("[0].name", notNullValue());
    }

    // ─── GET /store/{id} ───────────────────────────────────────────────────────

    @Test
    public void testGetSingleStore_nonExistentId_returns404() {
        given()
                .when().get("/store/999")
                .then()
                .statusCode(404)
                .body("code", equalTo(404))
                .body("error", containsString("999"));
    }

    // ─── POST /store ───────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testCreateStore_validPayload_returns201() {
        given()
                .contentType("application/json")
                .body("{\"name\": \"STOCKHOLM-TEST\", \"quantityProductsInStock\": 7}")
                .when().post("/store")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("STOCKHOLM-TEST"))
                .body("quantityProductsInStock", equalTo(7));
    }

    @Test
    @TestTransaction
    public void testCreateStore_withIdProvided_returns422() {
        given()
                .contentType("application/json")
                .body("{\"id\": 99, \"name\": \"SHOULD-FAIL\", \"quantityProductsInStock\": 1}")
                .when().post("/store")
                .then()
                .statusCode(422)
                .body("code", equalTo(422))
                .body("error", containsString("Id was invalidly set"));
    }

    // ─── PUT /store/{id} ───────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testUpdateStore_validPayload_returns200() {
        Integer storeId = createTestStore("UPDATE-TEST", 10);

        given()
                .contentType("application/json")
                .body("{\"name\": \"KALLAX-UPDATED\", \"quantityProductsInStock\": 20}")
                .when().put("/store/" + storeId)
                .then()
                .statusCode(200)
                .body("name", equalTo("KALLAX-UPDATED"))
                .body("quantityProductsInStock", equalTo(20));
    }

    @Test
    @TestTransaction
    public void testUpdateStore_updatesOnlyProvidedFields() {
        Integer storeId = createTestStore("PARTIAL-UPDATE", 15);

        given()
                .contentType("application/json")
                .body("{\"name\": \"NEW-NAME-ONLY\"}")
                .when().put("/store/" + storeId)
                .then()
                .statusCode(200)
                .body("name", equalTo("NEW-NAME-ONLY"));
    }

    @Test
    @TestTransaction
    public void testUpdateStore_nullName_returns422() {
        given()
                .contentType("application/json")
                .body("{\"quantityProductsInStock\": 5}")
                .when().put("/store/999")
                .then()
                .statusCode(422)
                .body("code", equalTo(422))
                .body("error", containsString("Store Name was not set"));
    }

    @Test
    public void testUpdateStore_nonExistentId_returns404() {
        given()
                .contentType("application/json")
                .body("{\"name\": \"GHOST\", \"quantityProductsInStock\": 1}")
                .when().put("/store/9999")
                .then()
                .statusCode(404)
                .body("code", equalTo(404))
                .body("error", containsString("9999"));
    }

    // ─── PATCH /store/{id} ─────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testPatchStore_updatesNameAndQuantity_whenBothPresent() {
        Integer storeId = createTestStore("PATCH-FULL", 10);

        given()
                .contentType("application/json")
                .body("{\"name\": \"TONSTAD-PATCHED\", \"quantityProductsInStock\": 99}")
                .when().patch("/store/" + storeId)
                .then()
                .statusCode(200)
                .body("name", equalTo("TONSTAD-PATCHED"))
                .body("quantityProductsInStock", equalTo(99));
    }

    @Test
    @TestTransaction
    public void testPatchStore_updatesOnlyName() {
        Integer storeId = createTestStore("PATCH-NAME", 50);

        given()
                .contentType("application/json")
                .body("{\"name\": \"PATCH-NAME-ONLY\"}")
                .when().patch("/store/" + storeId)
                .then()
                .statusCode(200)
                .body("name", equalTo("PATCH-NAME-ONLY"));
    }

   /* @Test
    @TestTransaction
    public void testPatchStore_updatesOnlyQuantity() {
        Integer storeId = createTestStore("PATCH-QUANTITY", 30);

        given()
                .contentType("application/json")
                .body("{\"quantityProductsInStock\": 999}")
                .when().patch("/store/" + storeId)
                .then()
                .statusCode(200)
                .body("quantityProductsInStock", equalTo(999));
    }*/

    @Test
    @TestTransaction
    public void testPatchStore_nullName_returns422() {
        given()
                .contentType("application/json")
                .body("{\"quantityProductsInStock\": 5}")
                .when().patch("/store/1")
                .then()
                .statusCode(422)
                .body("code", equalTo(422))
                .body("error", containsString("Store Name was not set"));
    }

    @Test
    public void testPatchStore_nonExistentId_returns404() {
        given()
                .contentType("application/json")
                .body("{\"name\": \"NOWHERE\", \"quantityProductsInStock\": 1}")
                .when().patch("/store/9999")
                .then()
                .statusCode(404)
                .body("code", equalTo(404));
    }

    // ─── DELETE /store/{id} ────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testDeleteStore_existingId_returns204AndStoreIsGone() {
        Integer newId = createTestStore("DELETE-ME-STORE", 1);

        given()
                .when().delete("/store/" + newId)
                .then()
                .statusCode(204);

        given()
                .when().get("/store/" + newId)
                .then()
                .statusCode(404);
    }

    @Test
    public void testDeleteStore_nonExistentId_returns404() {
        given()
                .when().delete("/store/9999")
                .then()
                .statusCode(404)
                .body("code", equalTo(404))
                .body("error", containsString("9999"));
    }

    // ─── ErrorMapper ──────────────────────────────────────────────────────────

    @Test
    public void testErrorMapper_returnsStructuredJsonWithCodeAndType() {
        given()
                .when().get("/store/99999")
                .then()
                .statusCode(404)
                .body("exceptionType", containsString("WebApplicationException"))
                .body("code", equalTo(404))
                .body("error", notNullValue());
    }

    // ─── Helper Method ────────────────────────────────────────────────────────

    private Integer createTestStore(String name, int quantity) {
        return given()
                .contentType("application/json")
                .body(String.format("{\"name\": \"%s\", \"quantityProductsInStock\": %d}", name, quantity))
                .when().post("/store")
                .then()
                .statusCode(201)
                .extract().path("id");
    }
}