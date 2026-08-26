import duckdb
import pandas as pd

from src.analytics_etl.load import load



def create_schema(conn):

    conn.execute(
        """
        CREATE TABLE DIM_ACCOUNT(
            account_key INTEGER PRIMARY KEY,
            account_id VARCHAR,
            account_type VARCHAR
        );


        CREATE TABLE DIM_INSTRUMENT(
            instrument_key INTEGER PRIMARY KEY,
            symbol VARCHAR,
            currency VARCHAR
        );


        CREATE TABLE DIM_DATE(
            date_key INTEGER PRIMARY KEY,
            full_date DATE,
            year INTEGER,
            month INTEGER,
            day INTEGER
        );


        CREATE TABLE FACT_TRADES(
            trade_key INTEGER,
            account_key INTEGER,
            instrument_key INTEGER,
            date_key INTEGER,

            open_price DOUBLE,
            high_price DOUBLE,
            low_price DOUBLE,
            close_price DOUBLE,
            adj_close DOUBLE,

            volume BIGINT,

            daily_return DOUBLE,
            daily_range DOUBLE,
            daily_range_pct DOUBLE,
            turnover DOUBLE
        );
        """
    )



def test_load_inserts_fact_rows(tmp_path):


    test_db = tmp_path / "test.duckdb"


    conn = duckdb.connect(str(test_db))


    create_schema(conn)


    conn.close()



    clean_df = pd.DataFrame(
        [
            {
                "symbol":"TEST.NS",
                "currency":"INR",
                "date":pd.Timestamp("2026-07-01"),

                "open":100,
                "high":110,
                "low":95,
                "close":105,
                "adjclose":105,

                "volume":1000,

                "daily_return":0.05,
                "daily_range":15,
                "daily_range_pct":0.15,
                "turnover":105000
            }
        ]
    )



    load(
        clean_df,
        db_path=str(test_db)
    )



    conn = duckdb.connect(str(test_db))


    count = conn.execute(
        """
        SELECT COUNT(*)
        FROM FACT_TRADES
        """
    ).fetchone()[0]


    assert count == 1