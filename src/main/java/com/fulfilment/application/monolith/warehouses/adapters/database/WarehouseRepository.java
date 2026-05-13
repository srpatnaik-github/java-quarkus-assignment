package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchQuery;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchPage;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public List<Warehouse> getAll() {
    return this.listAll().stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    DbWarehouse dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = warehouse.businessUnitCode;
    dbWarehouse.location = warehouse.location;
    dbWarehouse.capacity = warehouse.capacity;
    dbWarehouse.stock = warehouse.stock;
    dbWarehouse.createdAt = warehouse.createdAt;
    dbWarehouse.archivedAt = warehouse.archivedAt;
    
    this.persist(dbWarehouse);
  }

  /*@Override
  public void update(Warehouse warehouse) {
    getEntityManager().createQuery(
      "UPDATE DbWarehouse w SET w.location = :loc, w.capacity = :cap, " +
      "w.stock = :stock, w.archivedAt = :archived WHERE w.businessUnitCode = :code")
      .setParameter("loc", warehouse.location)
      .setParameter("cap", warehouse.capacity)
      .setParameter("stock", warehouse.stock)
      .setParameter("archived", warehouse.archivedAt)
      .setParameter("code", warehouse.businessUnitCode)
      .executeUpdate();

    // Clear persistence context to see updates in subsequent queries
    getEntityManager().flush();
    getEntityManager().clear();
  }*/

  @Override
  public void update(Warehouse warehouse) {
    // Step 1: Find the managed DbWarehouse entity — this brings it into
    // the JPA persistence context WITH its current @Version value
    DbWarehouse dbWarehouse = find("businessUnitCode", warehouse.businessUnitCode)
            .firstResult();

    if (dbWarehouse == null) {
      throw new IllegalArgumentException(
              "Warehouse not found for update: " + warehouse.businessUnitCode);
    }

    // Step 2: Map all fields from domain model onto the managed entity
    // DO NOT map version — JPA manages it internally on DbWarehouse
    dbWarehouse.location   = warehouse.location;
    dbWarehouse.capacity   = warehouse.capacity;
    dbWarehouse.stock      = warehouse.stock;
    dbWarehouse.archivedAt = warehouse.archivedAt;

    // Step 3: No explicit persist/merge needed — Panache/JPA dirty-checking
    // detects the field changes and at TX commit generates:
    // UPDATE warehouse SET stock=?, archived_at=?, ..., version=N+1
    // WHERE id=? AND version=N
    // If another TX already changed version → OptimisticLockException thrown
  }

  @Override
  public void remove(Warehouse warehouse) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'remove'");
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    DbWarehouse dbWarehouse = find("businessUnitCode", buCode).firstResult();
    return dbWarehouse != null ? dbWarehouse.toWarehouse() : null;
  }

  // ── NEW: search API() ─────────────────────────────────────────────────────────

  @Override
  public WarehouseSearchPage<Warehouse> search(WarehouseSearchQuery query) {

    // Always exclude archived warehouses
    StringBuilder jpql = new StringBuilder("archivedAt IS NULL");
    Map<String, Object> params = new HashMap<>();

    if (query.getLocation() != null && !query.getLocation().isBlank()) {
      jpql.append(" AND location = :location");
      params.put("location", query.getLocation());
    }

    if (query.getMinCapacity() != null) {
      jpql.append(" AND capacity >= :minCapacity");
      params.put("minCapacity", query.getMinCapacity());
    }

    if (query.getMaxCapacity() != null) {
      jpql.append(" AND capacity <= :maxCapacity");
      params.put("maxCapacity", query.getMaxCapacity());
    }

    String orderClause = " ORDER BY " + query.getSortBy()
            + " " + query.getSortOrder().toUpperCase();

    // Count total matching rows (without pagination)
    long totalElements = count(jpql.toString(), params);

    // Fetch the paginated slice, then map DbWarehouse → Warehouse
    List<Warehouse> data = find(jpql + orderClause, params)
            .page(query.getPage(), query.getPageSize())
            .list()
            .stream()
            .map(DbWarehouse::toWarehouse)
            .toList();

    return new WarehouseSearchPage<>(
            data,
            query.getPage(),
            query.getPageSize(),
            totalElements
    );
  }
}
