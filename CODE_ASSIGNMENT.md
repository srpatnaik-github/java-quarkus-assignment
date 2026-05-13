# Code Assignment - Senior Java Hackathon

**Time Expectation**: ~6 hours

**Before starting:**
- Read [BRIEFING.md](BRIEFING.md) for domain context
- Read [README.md](README.md) to understand the reference implementations
- Study the existing code patterns and tests

---

## Overview

This assignment focuses on **transaction management, concurrency handling, and optimistic locking** — critical skills for senior backend engineers.

The codebase contains implementations for Archive and Replace operations, along with a test suite. Your job is to understand the existing code, ensure all tests pass, and answer discussion questions.

> **Important**: The codebase may contain bugs and the test suite may not pass out of the box. Investigating failures, identifying root causes, and fixing the underlying code is part of the assignment.

---

## What's Already Implemented (Study These)

The codebase contains complete reference implementations for Archive and Replace operations:

### Archive Warehouse Operation
- `ArchiveWarehouseUseCase.java` - Complete implementation with validations
- `ArchiveWarehouseUseCaseTest.java` - Full test suite
- `WarehouseResourceImpl.archiveAWarehouseUnitByID()` - REST endpoint
- `WarehouseRepository.update()` - Database operations

**Implemented Business Rules**:
1. Only existing warehouses can be archived
2. Already-archived warehouses cannot be archived again
3. Archiving sets the `archivedAt` timestamp to current time
4. Proper error responses for validation failures

### Replace Warehouse Operation
- `ReplaceWarehouseUseCase.java` - Complete implementation with validations
- `ReplaceWarehouseUseCaseTest.java` - Full test suite
- `WarehouseResourceImpl.replaceTheCurrentActiveWarehouse()` - REST endpoint
- `WarehouseRepository.update()` - Database operations

**Implemented Business Rules**:
1. Only existing warehouses can be replaced
2. Archived warehouses cannot be replaced
3. New location must be valid (exists in the system)
4. New capacity cannot exceed location's max capacity
5. New stock cannot exceed new capacity

---

## Your Tasks

### Task 1: Study the Reference Implementation

**Goal**: Understand the existing code and architecture before attempting anything else.

**What to Study**:
1. **Archive Use Case** (`ArchiveWarehouseUseCase.java`) - validations, fields updated, repository interaction
2. **Replace Use Case** (`ReplaceWarehouseUseCase.java`) - validations, LocationResolver interaction, field handling
3. **Repository Layer** (`WarehouseRepository.java`) - how `create()` and `update()` are implemented and whether they behave consistently
4. **REST Endpoints** (`WarehouseResourceImpl.java`) - how endpoints wire use cases, exception handling, transaction boundaries
5. **Test Patterns** - study `ArchiveWarehouseUseCaseTest.java` and `ReplaceWarehouseUseCaseTest.java`, understand the full test coverage

---

### Task 2: Make All Tests Pass

**Goal**: Ensure the entire test suite passes — investigate root causes of any failures and fix the underlying code.

**Instructions**:
1. Run the full test suite: `./mvnw clean test`
2. Also run integration tests that aren't included by default (e.g., classes with `IT` suffix): `./mvnw test -Dtest=WarehouseConcurrencyIT,WarehouseTestcontainersIT`
3. Identify any failing tests, investigate their root causes, and fix the underlying code
4. Do whatever is needed — the goal is a fully working codebase where all tests pass consistently

**Success Criteria**:
- All tests pass when running `./mvnw clean test`
- All explicitly targeted integration tests also pass
- No flaky tests — results are consistent across multiple runs

---

### Task 3: Answer Discussion Questions

Answer both questions in [QUESTIONS.md](QUESTIONS.md):

**Question 1: API Specification Approaches**

The Warehouse API is defined in an OpenAPI YAML file from which code is generated. The `Product` and `Store` endpoints are hand-coded directly.

What are the pros and cons of each approach? Which would you choose and why?

**Question 2: Testing Strategy**

Given time and resource constraints, how would you prioritize tests for this project?

Which types of tests (unit, integration, parameterized, concurrency) would you focus on, and how would you ensure effective coverage over time?

---

### Bonus Task: Warehouse Search & Filter API

**If you complete the main tasks with time to spare**, implement a search and filter endpoint.

**Endpoint**:
```
GET /warehouse/search
```

**Query Parameters**:
| Parameter | Type | Description |
|---|---|---|
| `location` | `string` | Filter by location identifier (e.g. `AMSTERDAM-001`) |
| `minCapacity` | `integer` | Filter warehouses with capacity ≥ this value |
| `maxCapacity` | `integer` | Filter warehouses with capacity ≤ this value |
| `sortBy` | `string` | Sort field: `createdAt` (default) or `capacity` |
| `sortOrder` | `string` | `asc` or `desc` (default: `asc`) |
| `page` | `integer` | Page number, 0-indexed (default: `0`) |
| `pageSize` | `integer` | Page size (default: `10`, max: `100`) |

**Requirements**:
1. All parameters are optional
2. Archived warehouses must be excluded
3. Multiple filters use AND logic
4. Add integration test(s)

---

## Going Beyond

If you finish early or want to show more of what you can do — this is your space.

There are no fixed requirements here. Think about what a production-grade version of this system would look like and bring whatever you think adds value. Some prompts to get you thinking:

- Are there edge cases or failure modes not covered by the existing tests?
- Is there anything in the architecture, API design, or error handling you would do differently?
- What observability, resilience, or operational concerns would you address in a real system?
- Is there anything else in the codebase that looks off to you?

There are no wrong answers — we're interested in how you think and what you prioritise.

---

## Deliverables

1. **All tests passing**
   - Full test suite passes consistently
   - Any bugs found in the codebase are fixed
   - Integration tests (IT-suffix classes) also pass

2. **Answers to questions** in [QUESTIONS.md](QUESTIONS.md)
   - Thoughtful analysis of API specification approaches
   - Well-reasoned testing strategy

3. **(Bonus) Search endpoint** with tests
   - Working implementation
   - Proper pagination and filtering
   - Integration tests

---

## Available Locations

These are the predefined locations available in the system:

| Identifier | Max Warehouses | Max Capacity |
|---|---|---|
| ZWOLLE-001 | 1 | 40 |
| ZWOLLE-002 | 2 | 50 |
| AMSTERDAM-001 | 5 | 100 |
| AMSTERDAM-002 | 3 | 75 |
| TILBURG-001 | 1 | 40 |
| HELMOND-001 | 1 | 45 |
| EINDHOVEN-001 | 2 | 70 |
| VETSBY-001 | 1 | 90 |

---

## Running the Code

```bash
# Compile and run tests
./mvnw clean test

# Run specific test class
./mvnw test -Dtest=ArchiveWarehouseUseCaseTest

# Run specific test method
./mvnw test -Dtest=ArchiveWarehouseUseCaseTest#testConcurrentArchiveAndStockUpdateCausesOptimisticLockException

# Start development mode
./mvnw quarkus:dev

# Access Swagger UI
open http://localhost:8080/q/swagger-ui
```
