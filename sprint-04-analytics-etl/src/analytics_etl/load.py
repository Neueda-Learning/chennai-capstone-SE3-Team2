import duckdb
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parents[2]

DEFAULT_DB_PATH = BASE_DIR / "analytics.duckdb"


def load(clean_df, db_path=DEFAULT_DB_PATH):

    """
    Loads transformed analytical dataframe into DuckDB.

    Production:
        load(clean_df)
        -> writes to analytics.duckdb

    Testing:
        load(clean_df, db_path=test_database)
        -> writes to temporary database
    """


    conn = duckdb.connect(str(db_path))


    print("Using database:", db_path)



    # -----------------------------
    # DIM_ACCOUNT
    # -----------------------------

    conn.execute(
        """
        INSERT INTO DIM_ACCOUNT
        VALUES
        (
            1,
            'DEFAULT',
            'UNKNOWN'
        )
        ON CONFLICT DO NOTHING;
        """
    )



    # -----------------------------
    # DIM_INSTRUMENT
    # -----------------------------


    instruments = (
        clean_df[
            [
                "symbol",
                "currency"
            ]
        ]
        .drop_duplicates()
    )


    for _, row in instruments.iterrows():


        exists = conn.execute(
            """
            SELECT instrument_key
            FROM DIM_INSTRUMENT
            WHERE symbol=?
            AND currency=?
            """,
            (
                row["symbol"],
                row["currency"]
            )
        ).fetchone()



        if exists is None:


            key = conn.execute(
                """
                SELECT COALESCE(MAX(instrument_key),0)+1
                FROM DIM_INSTRUMENT
                """
            ).fetchone()[0]


            conn.execute(
                """
                INSERT INTO DIM_INSTRUMENT
                VALUES
                (?,?,?)
                """,
                (
                    key,
                    row["symbol"],
                    row["currency"]
                )
            )



    # -----------------------------
    # DIM_DATE
    # -----------------------------


    for date in clean_df["date"].unique():


        exists = conn.execute(
            """
            SELECT date_key
            FROM DIM_DATE
            WHERE full_date=?
            """,
            (date,)
        ).fetchone()



        if exists is None:


            key = conn.execute(
                """
                SELECT COALESCE(MAX(date_key),0)+1
                FROM DIM_DATE
                """
            ).fetchone()[0]



            conn.execute(
                """
                INSERT INTO DIM_DATE
                VALUES
                (?,?,?,?,?)
                """,
                (
                    key,
                    date,
                    date.year,
                    date.month,
                    date.day
                )
            )



    # -----------------------------
    # FACT_TRADES
    # -----------------------------


    for _, row in clean_df.iterrows():


        instrument_key = conn.execute(
            """
            SELECT instrument_key
            FROM DIM_INSTRUMENT
            WHERE symbol=?
            AND currency=?
            """,
            (
                row["symbol"],
                row["currency"]
            )
        ).fetchone()[0]



        date_key = conn.execute(
            """
            SELECT date_key
            FROM DIM_DATE
            WHERE full_date=?
            """,
            (
                row["date"],
            )
        ).fetchone()[0]



        trade_key = conn.execute(
            """
            SELECT COALESCE(MAX(trade_key),0)+1
            FROM FACT_TRADES
            """
        ).fetchone()[0]



        conn.execute(
            """
            INSERT INTO FACT_TRADES
            VALUES
            (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                trade_key,

                1,

                instrument_key,

                date_key,

                row["open"],
                row["high"],
                row["low"],
                row["close"],
                row["adjclose"],

                row["volume"],

                row["daily_return"],
                row["daily_range"],
                row["daily_range_pct"],
                row["turnover"]
            )
        )



    conn.close()