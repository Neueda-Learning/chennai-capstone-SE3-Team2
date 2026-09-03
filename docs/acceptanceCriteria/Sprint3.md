## Sprint 3

### SEC3-182 - Conceptual Data Model & ER Diagram

| Acceptance Criteria | Comments |
|---------------------|----------|
| Identify trading domain entities, attributes, and relationships | |
| Create an ER diagram covering entities, attributes, keys, cardinality, and optionality | `design/` |

### SEC3-183 - Normalised Relational Schema (1NF to 3NF)

| Acceptance Criteria | Comments |
|---------------------|----------|
| Schema is in 3NF with no redundant or transitive dependencies | |
| Document any deliberate denormalisation and its rationale | `design/` |

### SEC3-184 - Schema Migrations, Keys and Constraints

| Acceptance Criteria | Comments |
|---------------------|----------|
| Create schema using numbered SQL migrations in order | `migrations/` |
| Enforce domain rules with foreign keys and check constraints | |
| Enforce unique order idempotency key | |
| Include at least three check constraints, including account state | |

### SEC3-185 - One-Command Apply Script

| Acceptance Criteria | Comments |
|---------------------|----------|
| Apply migrations and seed an empty database with one command | |
| Read target from `TARGET_DATABASE`, falling back to `POSTGRES_DB` | |
| Exit non-zero on failure | |
| Declare the command in `manifest.env` | `APPLY_COMMAND` |

### SEC3-186 - Load the Provided Seed Data

| Acceptance Criteria | Comments |
|---------------------|----------|
| Load all provided seed data with one command | |
| Preserve referential integrity | |
| Load accounts in all three states | |

### SEC3-187 - The Six Named Queries and the Index Justifications

| Acceptance Criteria | Comments |
|---------------------|----------|
| Write and run all six named SQL queries | |
| Queries return sensible results | |
| Add and justify at least three indexes | `design/indexes.md` |

### SEC3-188 - Historical Trade Data Design

| Acceptance Criteria | Comments |
|---------------------|----------|
| Document retained trade data, grain, population, and owning component | `DESIGN.md` |
| Document incremental extraction and scalability at 100× volume | |
| Document write cost and operational complexity | |

### SEC3-189 - Harness Manifest and Probe Statements

| Acceptance Criteria | Comments |
|---------------------|----------|
| Declare required harness commands and names | `manifest.env` |
| Ensure the harness passes against the schema | |
| Add both required probe statements | |