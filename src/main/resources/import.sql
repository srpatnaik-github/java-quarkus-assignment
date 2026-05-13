-- =============================================
-- Initial Seed Data - FIXED for PostgreSQL + IDENTITY
-- =============================================

-- Stores
INSERT INTO store(id, name, quantityProductsInStock) VALUES (1, 'TONSTAD', 10);
INSERT INTO store(id, name, quantityProductsInStock) VALUES (2, 'KALLAX', 5);
INSERT INTO store(id, name, quantityProductsInStock) VALUES (3, 'BESTÅ', 3);

-- Products
INSERT INTO product(id, name, stock) VALUES (1, 'TONSTAD', 10);
INSERT INTO product(id, name, stock) VALUES (2, 'KALLAX', 5);
INSERT INTO product(id, name, stock) VALUES (3, 'BESTÅ', 3);

-- Warehouses (explicit ID + version = 0)
INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt, version)
VALUES (1, 'MWH.001', 'ZWOLLE-001', 100, 10, '2024-07-01', null, 0);

INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt, version)
VALUES (2, 'MWH.012', 'AMSTERDAM-001', 50, 5, '2023-07-01', null, 0);

INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt, version)
VALUES (3, 'MWH.023', 'TILBURG-001', 30, 27, '2021-02-01', null, 0);

-- Restart the CORRECT sequences used by GenerationType.IDENTITY
ALTER SEQUENCE store_seq RESTART WITH 100;
ALTER SEQUENCE product_seq RESTART WITH 100;
ALTER SEQUENCE warehouse_id_seq RESTART WITH 100;