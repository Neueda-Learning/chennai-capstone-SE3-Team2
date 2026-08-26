# Analytics ETL Pipeline

This project implements an ETL pipeline that:

1. Extracts raw market data
2. Transforms and validates the data
3. Loads cleaned analytical data into DuckDB
4. Stores data using a dimensional star schema


## Project Structure


contracts/
analytics-schema.sql -> DuckDB table definitions

src/analytics_etl/

extract.py              -> Data extraction
transform.py            -> Data cleaning and validation
load.py                 -> Loads data into DuckDB
pipeline.py             -> Runs complete ETL flow

tests/

test_extract.py
test_transform.py
test_load.py

---

# Requirements

## Python Version

Python 3.10+

---

# Setup Instructions

## 1. Clone repository


git clone <repository-url>

cd sprint-04-analytics-etl



# 2. Install Dependencies


Install required packages:


pip install duckdb


---

# DuckDB Setup


## What is DuckDB?

DuckDB is an embedded analytical database.

Unlike PostgreSQL/MySQL, it does not require a server.

The database exists as a single file:


analytics.duckdb


This file contains:

- tables
- schema
- inserted analytical data


Each user creates their own local DuckDB file.


---

# Creating the Analytical Schema


The database tables are defined in:


contracts/analytics-schema.sql

Run:
python test_duckdb.py

This SQL file creates the star schema:


## Dimension Tables

### DIM_ACCOUNT

Stores account information.

Grain:

One row per account.


### DIM_INSTRUMENT

Stores traded instruments.

Grain:

One row per unique symbol and currency combination.


### DIM_DATE

Stores calendar information.

Grain:

One row per date.


## Fact Table

### FACT_TRADES

Stores market trade metrics.

Grain:

One row per instrument per trading date.


Contains:

- open price
- high price
- low price
- close price
- volume
- daily return
- daily range
- turnover


---

# Create DuckDB Database


Run:

contracts/analytics-schema.sql


and creates:


analytics.duckdb



After creation, the database contains:


DIM_ACCOUNT
DIM_INSTRUMENT
DIM_DATE
FACT_TRADES



---

# Running the ETL Pipeline


Run:


python -m src.analytics_etl.pipeline



Pipeline flow:



Raw extracted JSON

    |
    v

extract.py

    |
    v

transform.py

    |
    v

clean dataframe

    |
    v

load.py

    |
    v

analytics.duckdb



---

# Running Tests


Run all tests:


pytest tests/test_transform.py -v
pytest tests/test_load.py -v



Tests cover:


## Transform

- valid data transformation
- missing fields
- invalid values
- malformed input handling


## Load

- loading into DuckDB
- dimension loading before facts
- fact table insertion


Tests use temporary DuckDB databases and do not modify production analytical data.


---

# Viewing DuckDB Data


Run:
python check_schema.py