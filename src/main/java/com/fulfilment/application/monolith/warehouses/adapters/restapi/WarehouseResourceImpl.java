package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchPage;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchQuery;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import com.warehouse.api.beans.WarehouseSearchResult;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import java.math.BigInteger;
import java.util.List;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject private WarehouseRepository warehouseRepository;
  @Inject private CreateWarehouseOperation createWarehouseOperation;
  @Inject private ArchiveWarehouseOperation archiveWarehouseOperation;
  @Inject private ReplaceWarehouseOperation replaceWarehouseOperation;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseRepository.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  @Override
  @Transactional
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    var domainWarehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    domainWarehouse.businessUnitCode = data.getBusinessUnitCode();
    domainWarehouse.location = data.getLocation();
    domainWarehouse.capacity = data.getCapacity();
    domainWarehouse.stock = data.getStock() != null ? data.getStock() : 0;

    try {
      createWarehouseOperation.create(domainWarehouse);
      return toWarehouseResponse(domainWarehouse);
    } catch (IllegalArgumentException e) {
      throw new WebApplicationException(e.getMessage(), 400);
    }
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    var domainWarehouse = warehouseRepository.findByBusinessUnitCode(id);

    if (domainWarehouse == null) {
      throw new WebApplicationException(
              "Warehouse with business unit code '" + id + "' not found", 404);
    }

    return toWarehouseResponse(domainWarehouse);
  }

  @Override
  @Transactional
  public void archiveAWarehouseUnitByID(String id) {
    var domainWarehouse = warehouseRepository.findByBusinessUnitCode(id);

    if (domainWarehouse == null) {
      throw new WebApplicationException(
              "Warehouse with business unit code '" + id + "' not found", 404);
    }

    try {
      archiveWarehouseOperation.archive(domainWarehouse);
    } catch (IllegalArgumentException e) {
      throw new WebApplicationException(e.getMessage(), 400);
    }
  }

  @Override
  @Transactional
  public Warehouse replaceTheCurrentActiveWarehouse(
          String businessUnitCode, @NotNull Warehouse data) {
    var domainWarehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    domainWarehouse.businessUnitCode = businessUnitCode;
    domainWarehouse.location = data.getLocation();
    domainWarehouse.capacity = data.getCapacity();
    domainWarehouse.stock = data.getStock() != null ? data.getStock() : 0;

    try {
      replaceWarehouseOperation.replace(domainWarehouse);
      var updated = warehouseRepository.findByBusinessUnitCode(businessUnitCode);
      return toWarehouseResponse(updated);
    } catch (IllegalArgumentException e) {
      int status = e.getMessage().contains("does not exist") ? 404 : 400;
      throw new WebApplicationException(e.getMessage(), status);
    }
  }

  @Override
  public WarehouseSearchResult searchWarehouses(
          String location,
          BigInteger minCapacity,
          BigInteger maxCapacity,
          String sortBy,
          String sortOrder,
          BigInteger page,
          BigInteger pageSize) {

    if (sortBy != null && !sortBy.equals("createdAt") && !sortBy.equals("capacity")) {
      throw new WebApplicationException("sortBy must be 'createdAt' or 'capacity'", 400);
    }

    if (sortOrder != null
            && !sortOrder.equalsIgnoreCase("asc")
            && !sortOrder.equalsIgnoreCase("desc")) {
      throw new WebApplicationException("sortOrder must be 'asc' or 'desc'", 400);
    }

    if (minCapacity != null && maxCapacity != null && minCapacity.compareTo(maxCapacity) > 0) {
      throw new WebApplicationException("minCapacity cannot be greater than maxCapacity", 400);
    }

    WarehouseSearchQuery query = WarehouseSearchQuery.builder()
            .location(location)
            .minCapacity(minCapacity != null ? minCapacity.intValue() : null)
            .maxCapacity(maxCapacity != null ? maxCapacity.intValue() : null)
            .sortBy(sortBy)
            .sortOrder(sortOrder)
            .page(page != null ? page.intValue() : null)
            .pageSize(pageSize != null ? pageSize.intValue() : null)
            .build();

    WarehouseSearchPage<com.fulfilment.application.monolith.warehouses.domain.models.Warehouse>
            domainResult = warehouseRepository.search(query);

    WarehouseSearchResult response = new WarehouseSearchResult();
    response.setData(domainResult.getData().stream().map(this::toWarehouseResponse).toList());
    response.setPage(domainResult.getPage());
    response.setPageSize(domainResult.getPageSize());
    response.setTotalElements(BigInteger.valueOf(domainResult.getTotalElements()));
    response.setTotalPages(domainResult.getTotalPages());

    return response;
  }

  private Warehouse toWarehouseResponse(
          com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);
    return response;
  }
}