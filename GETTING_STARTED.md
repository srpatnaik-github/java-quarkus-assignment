# Getting Started - Hackathon Java Assignment

## Prerequisites

- **JDK 17+** (verify with `java -version`)
- **Maven** (comes with `./mvnw`)
- **Docker** (optional, for PostgreSQL. Quarkus can start one for you!)
- **Your favorite IDE** (IntelliJ IDEA, VS Code, Eclipse)

## Step-by-Step Setup

### 1. Verify Java Version

```bash
java -version
# Should show Java 17 or higher
```

### 2. Build the Project

```bash
./mvnw clean install
```

This will:
- Download dependencies
- Compile source code
- Generate OpenAPI code
- Run tests
- Package the application

### 3. Start the Application

```bash
./mvnw quarkus:dev
```

Quarkus dev mode features:
- 🔥 **Hot reload** - Changes reflect immediately
- 🗄️ **Dev Services** - Auto-starts PostgreSQL if needed
- 🧪 **Continuous testing** - Press 'r' to run tests
- 📊 **Dev UI** - http://localhost:8080/q/dev

### 4. Explore the API

Open Swagger UI: http://localhost:8080/q/swagger-ui

Try these endpoints:
- `GET /warehouse` - List all warehouses
- `POST /warehouse` - Create a warehouse
- `GET /warehouse/{id}` - Get warehouse by business unit code
- `GET /store` - List all stores
- `GET /product` - List all products

### 5. Run Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=WarehouseValidationTest

# Run integration tests
./mvnw verify
```

## Understanding the Codebase

### 30-Minute Orientation Plan

**Minutes 0-10: Read Documentation**
1. [BRIEFING.md](BRIEFING.md) - Domain overview
2. [CODE_ASSIGNMENT.md](CODE_ASSIGNMENT.md) - Task descriptions

**Minutes 10-20: Explore Code Structure**
```
Start here:
1. location/LocationGateway.java       (simplest implementation)
2. stores/StoreResource.java           (event firing)
3. stores/StoreEventObserver.java      (event handling)
4. warehouses/domain/usecases/CreateWarehouseUseCase.java
5. warehouses/adapters/restapi/WarehouseResourceImpl.java
```

**Minutes 20-30: Run and Understand Tests**
```bash
# Run each test and observe behavior
./mvnw test -Dtest=StoreEventObserverTest
./mvnw test -Dtest=WarehouseValidationTest
./mvnw test -Dtest=WarehouseOptimisticLockingTest
```

## Common Tasks

### Create a Warehouse via API

```bash
curl -X POST http://localhost:8080/warehouse \
  -H "Content-Type: application/json" \
  -d '{
    "businessUnitCode": "WH-001",
    "location": "AMSTERDAM-001",
    "capacity": 50,
    "stock": 10
  }'
```

### Query Available Locations

Locations are hardcoded in `LocationGateway.java`:
- ZWOLLE-001, ZWOLLE-002
- AMSTERDAM-001, AMSTERDAM-002
- TILBURG-001
- HELMOND-001
- EINDHOVEN-001
- VETSBY-001

### Understanding Validations

When creating a warehouse, these validations apply:
1. ✅ Business unit code must be unique
2. ✅ Location must exist (see list above)
3. ✅ Capacity ≤ Location's max capacity
4. ✅ Stock ≤ Warehouse capacity

Try creating invalid warehouses to see validation errors!

## IDE Setup

### IntelliJ IDEA

1. **Import Project**: File → Open → Select `pom.xml`
2. **Enable Annotation Processing**: Build → Compiler → Annotation Processors → Enable
3. **Add Generated Sources**: 
   - Right-click `target/generated-sources/openapi/src/main/java`
   - Mark Directory As → Generated Sources Root

### VS Code

1. **Install Extensions**:
   - Extension Pack for Java
   - Quarkus Tools
2. **Open Folder**: Open the hackathon-java-assignment directory
3. **Run Configuration**: Use Quarkus extension to run/debug

## Debugging Tips

### Enable Detailed Logging

Edit `src/main/resources/application.properties`:
```properties
quarkus.log.level=DEBUG
quarkus.hibernate-orm.log.sql=true
```

### Debug a Test

In IDE, right-click test method → Debug

Or use Maven:
```bash
./mvnw test -Dmaven.surefire.debug
```

Then attach debugger to port 5005.

### Check Database State

Quarkus Dev UI has H2 console: http://localhost:8080/q/dev

## Troubleshooting

### Port 8080 Already in Use

```bash
# Change port in application.properties
quarkus.http.port=8081
```

### Tests Failing with Database Errors

```bash
# Clean and rebuild
./mvnw clean install

# Or start fresh Docker PostgreSQL
docker run -d --name hackathon-pg \
  -e POSTGRES_USER=quarkus \
  -e POSTGRES_PASSWORD=quarkus \
  -e POSTGRES_DB=quarkus \
  -p 5432:5432 postgres:13
```

### OpenAPI Code Not Generated

```bash
# Force regeneration
./mvnw clean compile
```

## Next Steps

Once you're comfortable:

1. 📚 **Study the sophisticated tests** - They teach patterns!
2. 🧪 **Try breaking things** - Change code, see what fails
3. 🎯 **Attempt the bonus task** - Warehouse search/filter
4. 📝 **Answer the questions** - In QUESTIONS.md

## Resources

- [Quarkus Guides](https://quarkus.io/guides/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers](https://www.testcontainers.org/)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

**Questions?** Dive into the code!

**Ready?** Let's go! 🚀
