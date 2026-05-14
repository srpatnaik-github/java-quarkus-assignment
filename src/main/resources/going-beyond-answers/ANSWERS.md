# Answers to the going beyond questions

1. Edge Cases & Test Coverage
   The tests are decent for a time-boxed assignment, but few scenarios are missed:

    - Archiving an already archived warehouse
    - Concurrent archive + update/replace operations
    - Handling transient database failures
    - Input validation for extreme values (negative capacity, very long strings, etc.)
   

2. Architecture & API Design
   Overall the hexagonal architecture and event-driven legacy sync are clean. However, I would improve:

    - Error handling — currently a mix of WebApplicationException and raw IllegalArgumentException. A custom exception hierarchy + consistent Problem JSON responses would be better.
    - Some business logic is leaking into the resource/impl layer instead of staying in the repository or use-case.
    - PATCH vs PUT semantics in StoreResource could be more clearly documented.
   

3. Production-Grade Concerns
   In a real production system I would add:

    - Proper observability (metrics, structured logging with correlation IDs, health checks)
    - Resilience patterns (@Retry, @CircuitBreaker) especially for external/legacy calls
    - Better operational practices (graceful shutdown, feature flags, audit logging of business events)
   

4. Other Observations

    - import.sql was quite fragile and caused multiple startup issues — needs cleaner handling for both H2 and PostgreSQL.
    - The legacy gateway simulation is acceptable for a hackathon but should be properly abstracted in a real system.

