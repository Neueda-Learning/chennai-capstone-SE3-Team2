import duckdb
from pathlib import Path


conn = duckdb.connect("analytics.duckdb")


schema = Path(
    "contracts/analytics-schema.sql"
).read_text()


conn.execute(schema)


print("Schema created successfully")


tables = conn.execute(
    """
    SHOW TABLES;
    """
).fetchall()


print(tables)


conn.close()