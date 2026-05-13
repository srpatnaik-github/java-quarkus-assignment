package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
public class WarehouseSearchIT {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void seedData() {
        // Clean slate before every test — no interference from other test classes
        em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
        em.flush();

        insertWarehouse("MWH.001", "ZWOLLE-001",    100, 10,  null);
        insertWarehouse("MWH.012", "AMSTERDAM-001",  50,  5,  null);
        insertWarehouse("MWH.023", "TILBURG-001",    30, 27,  null);
    }

    private void insertWarehouse(String code, String location, int capacity, int stock,
                                 LocalDateTime archivedAt) {
        DbWarehouse w = new DbWarehouse();
        w.businessUnitCode = code;
        w.location         = location;
        w.capacity         = capacity;
        w.stock            = stock;
        w.createdAt        = LocalDateTime.now();
        w.archivedAt       = archivedAt;
        em.persist(w);
        em.flush();
    }

    // ── No-filter tests ───────────────────────────────────────────────────────

    @Test
    void search_noParams_returnsAllActiveWarehouses() {
        given()
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(3))
                .body("page",          equalTo(0))
                .body("pageSize",      equalTo(10))
                .body("totalElements", equalTo(3))
                .body("totalPages",    equalTo(1));
    }

    // ── Location filter tests ─────────────────────────────────────────────────

    @Test
    void search_filterByExactLocation_returnsMatchingWarehouse() {
        given()
                .queryParam("location", "AMSTERDAM-001")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",              equalTo(1))
                .body("data[0].businessUnitCode", equalTo("MWH.012"))
                .body("totalElements",            equalTo(1));
    }

    @Test
    void search_filterByUnknownLocation_returnsEmptyResult() {
        given()
                .queryParam("location", "NOWHERE-999")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(0))
                .body("totalElements", equalTo(0));
    }

    // ── Capacity filter tests ─────────────────────────────────────────────────

    @Test
    void search_filterByMinCapacity_returnsWarehousesAboveThreshold() {
        given()
                .queryParam("minCapacity", 50)
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(2))   // capacity 50 and 100
                .body("totalElements", equalTo(2));
    }

    @Test
    void search_filterByMaxCapacity_returnsWarehousesBelowThreshold() {
        given()
                .queryParam("maxCapacity", 50)
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(2))   // capacity 30 and 50
                .body("totalElements", equalTo(2));
    }

    @Test
    void search_filterByCapacityRange_returnsWarehousesWithinRange() {
        given()
                .queryParam("minCapacity", 30)
                .queryParam("maxCapacity", 50)
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(2))   // capacity 30 and 50
                .body("totalElements", equalTo(2));
    }

    // ── Sort tests ────────────────────────────────────────────────────────────

    @Test
    void search_sortByCapacityDesc_returnsInDescendingOrder() {
        given()
                .queryParam("sortBy",    "capacity")
                .queryParam("sortOrder", "desc")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data[0].capacity", equalTo(100))
                .body("data[1].capacity", equalTo(50))
                .body("data[2].capacity", equalTo(30));
    }

    @Test
    void search_sortByCapacityAsc_returnsInAscendingOrder() {
        given()
                .queryParam("sortBy",    "capacity")
                .queryParam("sortOrder", "asc")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data[0].capacity", equalTo(30))
                .body("data[1].capacity", equalTo(50))
                .body("data[2].capacity", equalTo(100));
    }

    // ── Pagination tests ──────────────────────────────────────────────────────

    @Test
    void search_pagination_returnsCorrectFirstPage() {
        given()
                .queryParam("page",     0)
                .queryParam("pageSize", 2)
                .queryParam("sortBy",    "capacity")
                .queryParam("sortOrder", "asc")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(2))
                .body("page",          equalTo(0))
                .body("pageSize",      equalTo(2))
                .body("totalElements", equalTo(3))
                .body("totalPages",    equalTo(2));
    }

    @Test
    void search_secondPage_returnsRemainingItems() {
        given()
                .queryParam("page",      1)
                .queryParam("pageSize",  2)
                .queryParam("sortBy",    "capacity")
                .queryParam("sortOrder", "asc")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",   equalTo(1))
                .body("page",          equalTo(1))
                .body("totalElements", equalTo(3))
                .body("totalPages",    equalTo(2));
    }

    @Test
    void search_pageSizeExceedsMax_isCappedAt100() {
        given()
                .queryParam("pageSize", 999)
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("pageSize", equalTo(100));
    }

    // ── Archived warehouse exclusion test ─────────────────────────────────────

    @Test
    void search_archivedWarehousesNeverAppear() {
        // Archive MWH.001 via the API using its businessUnitCode
        given()
                .when().delete("/warehouse/MWH.001")
                .then().statusCode(204);

        given()
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("totalElements",         equalTo(2))
                .body("data.businessUnitCode", not(hasItem("MWH.001")));
    }

    // ── Validation / 400 tests ────────────────────────────────────────────────

    @Test
    void search_invalidSortBy_returns400() {
        given()
                .queryParam("sortBy", "invalidField")
                .when().get("/warehouse/search")
                .then()
                .statusCode(400);
    }

    @Test
    void search_invalidSortOrder_returns400() {
        given()
                .queryParam("sortOrder", "random")
                .when().get("/warehouse/search")
                .then()
                .statusCode(400);
    }

    @Test
    void search_minCapacityGreaterThanMaxCapacity_returns400() {
        given()
                .queryParam("minCapacity", 100)
                .queryParam("maxCapacity", 10)
                .when().get("/warehouse/search")
                .then()
                .statusCode(400);
    }

    // ── Combined filter test ──────────────────────────────────────────────────

    @Test
    void search_combinedFilters_appliesAndLogic() {
        given()
                .queryParam("location",    "AMSTERDAM-001")
                .queryParam("minCapacity", 40)
                .queryParam("sortBy",      "capacity")
                .queryParam("sortOrder",   "asc")
                .when().get("/warehouse/search")
                .then()
                .statusCode(200)
                .body("data.size()",              equalTo(1))
                .body("data[0].businessUnitCode", equalTo("MWH.012"));
    }
}