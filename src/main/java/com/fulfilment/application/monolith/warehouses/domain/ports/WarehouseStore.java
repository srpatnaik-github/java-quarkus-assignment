package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchQuery;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchPage;

import java.util.List;

public interface WarehouseStore {

  List<Warehouse> getAll();

  void create(Warehouse warehouse);

  void update(Warehouse warehouse);

  void remove(Warehouse warehouse);

  Warehouse findByBusinessUnitCode(String buCode);

  // New method signature added for Warehouse search API
  WarehouseSearchPage<Warehouse> search(WarehouseSearchQuery query);

}
