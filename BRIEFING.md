# Domain Briefing - Fulfillment System

## Overview

This is a simplified fulfillment management system that handles the relationship between physical locations, warehouses, stores, and products. The system is designed to help manage inventory and distribution across multiple locations.

## Core Entities

### Location

Represents a physical geographic location where warehouses can be established.

**Key Attributes:**
- **Identifier**: Unique location code (e.g., "AMSTERDAM-001", "ZWOLLE-002")
- **Name**: Human-readable name (e.g., "Amsterdam Warehouse District")
- **Max Warehouses**: Maximum number of warehouses allowed at this location
- **Max Capacity**: Maximum total capacity allowed at this location

**Business Rules:**
- Locations are predefined in the system (8 locations available)
- Each location has constraints on how many warehouses it can support
- Total warehouse capacity at a location cannot exceed the location's max capacity

### Warehouse

A storage facility at a specific location that holds inventory.

**Key Attributes:**
- **ID**: Auto-generated unique identifier (Long)
- **Business Unit Code**: Unique business code for the warehouse
- **Location**: The location where this warehouse is situated
- **Capacity**: Storage capacity of the warehouse
- **Stock**: Current inventory quantity stored
- **Created At**: Timestamp when warehouse was created
- **Archived At**: Timestamp when warehouse was archived (null if active)

**Business Rules:**
- Each warehouse must have a unique business unit code
- Warehouse location must be valid (must exist in the system)
- Warehouse capacity cannot exceed what the location allows
- Warehouse stock cannot exceed its capacity
- Archived warehouses cannot be modified (replaced)
- Once archived, a warehouse cannot be un-archived

### Store

A retail location that receives products from warehouses.

**Key Attributes:**
- **ID**: Unique identifier
- **Name**: Store name
- **Quantity Products In Stock**: Current product count

**Business Rules:**
- Store changes must be synchronized with a legacy system
- **Critical**: Legacy system sync must happen AFTER database transaction commits
- This ensures the legacy system never receives uncommitted data

### Product

Items that are stored in warehouses and sold through stores.

**Key Attributes:**
- **ID**: Unique identifier
- **Name**: Product name
- **Stock**: Quantity available

## System Architecture Notes

The codebase follows **Hexagonal Architecture (Ports and Adapters)**:

- **Domain Layer**: Business logic and use cases
- **Adapters Layer**: REST API endpoints, database repositories
- **Ports**: Interfaces defining contracts between layers

## Technology Stack

- **Framework**: Quarkus 3.13.3
- **Language**: Java 17+
- **Database**: PostgreSQL
- **ORM**: Hibernate with Panache
- **Testing**: JUnit 5, RestAssured, Testcontainers
- **API Spec**: OpenAPI (for Warehouse endpoints)

## Key Integration Points

### Legacy System Integration

The system integrates with a legacy store management system:
- Changes to stores must be propagated to the legacy system
- The legacy system is simulated by writing to temporary files
- **Important**: This integration must respect transaction boundaries

## Getting Started

Before diving into the tasks:

1. Review the existing code structure
2. Understand the hexagonal architecture pattern
3. Look at the test examples to understand testing patterns
4. Check how transactions and database operations are handled

Good luck! 🚀
