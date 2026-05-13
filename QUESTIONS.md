# Questions

Here are 2 questions related to the codebase. There's no right or wrong answer - we want to understand your reasoning.

## Question 1: API Specification Approaches

When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded everything directly. 

What are your thoughts on the pros and cons of each approach? Which would you choose and why?

**Answer:**
```txt
So looking at this assignment, I found it really interesting that we ended up experiencing both approaches side by side — almost like a natural experiment. The Warehouse API was driven by an OpenAPI YAML spec where code gets generated from it, while Product and Store I just coded directly in Java from scratch. Having worked with both in the same project, here's my honest take.

The Spec-First Approach (Warehouse)
This is where you sit down and design the contract — the YAML file — before writing a single line of implementation code. The tooling (quarkus-openapi-generator in our case) then generates Java interface stubs, and your job is just to implement them.

What I liked about it:

The biggest thing for me was that the YAML became the single source of truth. Anyone — whether it's a frontend dev, a mobile team, or an external consumer — can look at that one file and know exactly what the API does. Nobody has to dig through Java code trying to reverse-engineer what a method actually returns.

It also forces good design upfront. When I was working on the Warehouse endpoints, the generated interface was essentially a compiler-enforced checklist. If my implementation didn't match the spec, it simply wouldn't compile. That's a really powerful safety net.

And honestly, documentation becomes free. The Swagger UI in this project automatically reflects the spec. I didn't have to write a single line of documentation separately.

Where it gets uncomfortable:

Writing a good OpenAPI spec takes time and skill. If you're in early prototype mode trying to figure out what the API should even look like, sitting down to write a complete YAML first can feel like premature formalism. There's also a bit of a mental context switch — debugging means jumping between the YAML, the generated interface, and your actual implementation. We felt a hint of that friction in this assignment.

The Code-First Approach (Product & Store)
Here I just wrote the @Path, @GET, @POST annotations directly on the Java class and went from there. The API shape is whatever the code says it is.

What worked well:

It was genuinely fast to get going. StoreResource and ProductResource came together quickly, and iterating on them felt natural. When you're in "figure it out as you go" mode, this approach matches that energy.

Where it bit us:

Honestly, the test failures we debugged yesterday are a perfect example of code-first problems in action. Remember the testGetSingleStore_existingId_returns200 test failing because TONSTAD had been mutated to TONSTAD-PATCHED by a previous test? That's ultimately a contract problem. Because the API shape was informal and test isolation wasn't enforced, state bled between tests and things broke in confusing ways.

I also noticed that Product and Store ended up slightly inconsistent with each other — their error handling, response shapes, and validation patterns diverged in small ways. That's fine in a hackathon, but in a real team with 10 services, that inconsistency multiplies fast and becomes painful.

What Would I Choose?
Spec-first — but I'd be pragmatic about timing.

For a project like this one, I'd use a single OpenAPI YAML covering all three resources — Warehouse, Product, and Store together. Not three separate specs, not a mix of approaches. One file, one contract, one Swagger UI that tells the full story of the service.

The reason I feel strongly about this is what I experienced firsthand here: spec-first gave me compile-time safety on Warehouse that I simply didn't have on Product and Store. When I refactored something on the Warehouse side, the compiler caught mismatches immediately. When I changed something on the Store side, the only way I found out was when a test broke at runtime — and as we saw, debugging that took significantly longer.

The one concession I'd make: if I'm truly in early exploration mode — like day one of a new feature where I'm not even sure what the endpoints should be — I might sketch things out code-first to think through the design. But the moment things stabilize, I'd write the spec, generate the interfaces, and migrate the implementations to match. Spec-first shouldn't feel like bureaucracy; it should feel like having the blueprint before you start building the house.
```
## Question 2: Testing Strategy

Given the need to balance thorough testing with time and resource constraints, how would you prioritize tests for this project? 

Which types of tests (unit, integration, parameterized, etc.) would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
I want to anchor this in something what we actually built in this assignment. 
We had StoreResourceTest running as a @QuarkusTest, WarehouseEndpointIT as a @QuarkusIntegrationTest, Hibernate recreating tables on every run, 
a StoreEventObserver firing side effects, and JaCoCo measuring coverage. The failures we hit along the way — state bleeding between tests, Mockito callback mismatches — taught me more about testing strategy.

Integration tests on the REST layer give the highest value for a service like this. 
The core job of this application is exposing a well-behaved HTTP API over a database. 
If the endpoints return wrong status codes or bad response bodies, nothing else matters. 
So StoreResourceTest, WarehouseEndpointIT, and a similar suite for ProductResource are non-negotiable.

That said, I would consolidate repetitive scenarios with parameterized tests. 
In our Store tests, we separately tested 404s for GET, PUT, PATCH, and DELETE on non-existent IDs — that's the same assertion four times. One @ParameterizedTest handles all of them cleanly.

Unit Tests for Business Logic
The validation rules — "is the warehouse already archived?", "is the name set?", "is the ID invalid on creation?" — are pure business logic and don't need a running server. 
I would extract those into service classes and unit test them with JUnit 5 + Mockito. 
They run in milliseconds, give precise feedback, and work in CI without a database.

Keeping Coverage Meaningful
JaCoCo is already wired in, but a 70% coverage number means nothing if it's all happy paths. 
I would rather have 60% that covers every error branch and validation rule than 90% that just calls every method once with valid data.

My practical rules:
--Write tests in the same commit as the code change — you know the edge cases best in the moment.
--Make isolation non-negotiable — the @BeforeEach DB reset we added is exactly right; every test starts clean automatically.
--Exclude generated and boilerplate code from JaCoCo so the metric reflects your actual logic.

My Priority Order:
--REST integration tests (@QuarkusTest + REST-assured) — highest value.
--Unit tests for business/validation logic — fastest feedback.
--Parameterized tests to consolidate repetitive scenarios.
--One @QuarkusIntegrationTest smoke test — confirms the packaged JAR works.
--Contract tests if other services consume this API — future investment.


```
