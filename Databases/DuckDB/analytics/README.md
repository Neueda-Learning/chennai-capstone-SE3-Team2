# DuckDB analytics database

The analytics ETL (`ETL Layer/`) writes its star schema here, to
`analytics.duckdb`. The file is built, never committed: run the pipeline to
create it (see the repository README, "Analytics").

The path is `DUCKDB_PATH` in `.env`; this folder is its default.
