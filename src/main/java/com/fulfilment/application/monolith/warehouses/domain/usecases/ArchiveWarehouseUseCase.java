package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  @Override
  @Transactional
  public void archive(Warehouse input) {
    // Load fresh managed entity
    Warehouse existing = warehouseStore.findByBusinessUnitCode(input.businessUnitCode);

    if (existing == null) {
      throw new IllegalArgumentException(
              "Warehouse with business unit code '" + input.businessUnitCode + "' does not exist");
    }

    if (existing.archivedAt != null) {
      throw new IllegalArgumentException(
              "Warehouse with business unit code '" + input.businessUnitCode + "' is already archived");
    }

    // Mark as archived
    existing.archivedAt = java.time.LocalDateTime.now();

    // Update through repository
    warehouseStore.update(existing);
  }
}