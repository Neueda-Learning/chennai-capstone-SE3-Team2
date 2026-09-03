### Sprint 5

### SEC3-200 - Maven Project and the Dependency Ban

| Acceptance Criteria | Comments |
|---------------------|----------|
| Maven project builds with `mvn clean test-compile` | |
| Allow only `jakarta.validation-api` as a non-test dependency | |
| Exclude banned dependencies, including transitively | Spring, servlet API, JDBC, MyBatis, connection pool |

### SEC3-201 - Domain Modelling (UML Class and Sequence Diagrams)

| Acceptance Criteria | Comments |
|---------------------|----------|
| Create class diagram covering all domain types and relationships | `design/` |
| Create sequence diagram covering order flow and eight rules | |
| Ensure diagrams match the code | |

### SEC3-202 - Domain Entities

| Acceptance Criteria | Comments |
|---------------------|----------|
| Implement Account, Instrument, Order, and Position | |
| Prevent negative balances and use two-decimal money | |
| Prevent negative positions and invalid order transitions | |

### SEC3-203 - Domain Enumerations

| Acceptance Criteria | Comments |
|---------------------|----------|
| Implement `AccountStatus`, `OrderSide`, and `OrderStatus` with exact contract literals | |

### SEC3-204 - Order Request DTO with Validation

| Acceptance Criteria | Comments |
|---------------------|----------|
| Implement DTO with all six fields and contract constraints | |
| Test all validation boundaries | |

### SEC3-205 - Exception Hierarchy

| Acceptance Criteria | Comments |
|---------------------|----------|
| Implement six exceptions under one domain base type | |
| Base exception carries catalogue code and catalogue message only | |

### SEC3-206 - Business Rules 1 to 8

| Acceptance Criteria | Comments |
|---------------------|----------|
| Implement rules 1–8 in the domain and enforce them in order | |
| Return the first failing rule's code | |

### SEC3-207 - Test-First JUnit Suite

| Acceptance Criteria | Comments |
|---------------------|----------|
| Keep `AccountTest`, `OrderLogicTest`, and `PlaceOrderRequestValidationTest` green | 40 tests |
| Test commits precede the code commits that satisfy them |          |