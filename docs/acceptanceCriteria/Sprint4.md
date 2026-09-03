## Sprint 4

### SEC3-191 - Python Project and the Engineering Contract
| Acceptance Criteria | Comments |
| ----------------------|------|
| ETL package installs successfully from the repository root | `sprint-04-analytics-etl` |
| Dev dependencies include pytest, and pytest discovers tests in `tests/` |  |

### SEC3-192 - Fauxnance Client, Key Handling and Caching
| Acceptance Criteria | Comments |
|---------------------|------|
| Fetch candles via API key header |  |
| Cache raw responses by symbol and range |  |

### SEC3-193 - Symbol Universe including NSE or BSE Instruments
| Acceptance Criteria | Comments |
|---------------------|----------|
| Include at least two NSE/BSE instruments | |
| Keep the universe small enough for three defensible claims | |

### SEC3-194 - Extract, Transform and Load as Three Modules
| Acceptance Criteria | Comments |
|---------------------|----------|
| ETL stages are separate modules, wired by a fourth module | `pipeline` |
| Transform has no I/O; load is the only writer | |

### SEC3-195 - Error Handling for the Four Failure Modes
| Acceptance Criteria | Comments |
|---------------------|----------|
| Handle and distinguish all four failure modes | 429, other 4xx, connection/timeout, bad payload |
| Log the failure type without exposing the API key | |

### SEC3-196 - Transform Tests against the Provided Fixtures
| Acceptance Criteria | Comments |
|---------------------|----------|
| Test transform with pytest using offline fixtures | |
| Include malformed-input coverage and standalone execution | `manifest.env` |

### SEC3-197 - Three Business Claims and the Charts that Support Them
| Acceptance Criteria | Comments |
|---------------------|----------|
| Document 3+ business insights with supporting charts | `claims.md` - TATASTEEL, INFY, |
| Charts have labelled axes, units, findings-based titles, and clear names | |
| Charts open offline without a build step | |

### SEC3-198 - Define and Load the Analytical Store
| Acceptance Criteria | Comments |
|---------------------|----------|
| Create and load the dimensional model in the analytical store | `contracts/analytics-schema.sql` |
| Document the star schema and its grain | |