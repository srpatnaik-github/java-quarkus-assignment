package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class WarehouseEndpointIT {

    private static final String PATH = "/warehouse";

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    public void setup() {
        em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
    }

    @Test
    public void testListAllWarehouses() {
        createWarehouseDirectly("WH-LIST-001", "AMSTERDAM-001", 100, 20);
        createWarehouseDirectly("WH-LIST-002", "ZWOLLE-001", 50, 10);

        given()
                .when().get(PATH)
                .then()
                .statusCode(200)
                .body(containsString("WH-LIST-001"))
                .body(containsString("WH-LIST-002"));
    }

    @Test
    public void testArchiveWarehouse() {
        String code = "WH-ARCHIVE-TEST-001";

        createWarehouseDirectly(code, "TEST-LOCATION", 100, 50);

        given()
                .when().delete(PATH + "/" + code)
                .then()
                .statusCode(204);

        // Verify archiving worked by checking archivedAt in database
        assertTrue(isWarehouseArchived(code),
                "Warehouse should have archivedAt set after archive operation");
    }

    @Test
    public void testArchiveWarehouse_notFound_shouldReturn404() {
        String nonExistent = "WH-NON-EXISTENT-999";

        given()
                .when().delete(PATH + "/" + nonExistent)
                .then()
                .statusCode(404)
                .body(containsString("Warehouse with business unit code '" + nonExistent + "' not found"));
    }

    @Test
    public void testGetSingleWarehouse() {
        String code = "WH-GET-001";
        createWarehouseDirectly(code, "AMSTERDAM-001", 60, 15);

        given()
                .when().get(PATH + "/" + code)
                .then()
                .statusCode(200)
                .body(containsString(code));
    }

    // ==================== Helper Methods ====================

    @Transactional
    void createWarehouseDirectly(String businessUnitCode, String location, int capacity, int stock) {
        em.createNativeQuery("""
            INSERT INTO warehouse 
            (businessUnitCode, location, capacity, stock, createdAt, version) 
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 0)
            """)
                .setParameter(1, businessUnitCode)
                .setParameter(2, location)
                .setParameter(3, capacity)
                .setParameter(4, stock)
                .executeUpdate();
    }

    @Transactional
    boolean isWarehouseArchived(String businessUnitCode) {
        Long count = (Long) em.createNativeQuery("""
            SELECT COUNT(*) FROM warehouse 
            WHERE businessUnitCode = ? AND archivedAt IS NOT NULL
            """)
                .setParameter(1, businessUnitCode)
                .getSingleResult();

        return count > 0;
    }
}