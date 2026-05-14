package com.fulfilment.application.monolith.warehouses.domain;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.usecases.CreateWarehouseUseCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class RemoveWarehouseTest {

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

    /**
     * The below test case is for testing the remove functionality which might be used
     * in the future as currently we have only archive and replace functionalities
     * without a HARD DELETE.
     */
    @Test
    @Transactional
    public void testCreateAndRemoveWarehouseWithValidDataSucceeds() {
        Warehouse warehouse = buildWarehouse();

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
        assertDoesNotThrow(() -> warehouseRepository.remove(warehouse));
    }

    private Warehouse buildWarehouse() {
        Warehouse w = new Warehouse();
        w.businessUnitCode = "CREATE-001";
        w.location         = "AMSTERDAM-001";
        w.capacity         = 50;
        w.stock            = 20;
        return w;
    }
}
