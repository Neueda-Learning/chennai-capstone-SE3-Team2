import duckdb

conn = duckdb.connect("analytics.duckdb")

print("Tables:")
print(
    conn.execute(
        "SHOW TABLES"
    ).fetchall()
)


for table in [
    "DIM_ACCOUNT",
    "DIM_INSTRUMENT",
    "DIM_DATE",
    "FACT_TRADES"
]:
    print("\n----------------")
    print(table)

    print(
        conn.execute(
            f"DESCRIBE {table}"
        ).fetchall()
    )
    print(
        conn.execute(
            f"SELECT * FROM {table}"
        
        ).fetchall()
    )

conn.close()