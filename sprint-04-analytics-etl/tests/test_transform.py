import pytest

from src.analytics_etl.transform import transform
#To run this, use command pytest test_transform.py in the folder it is placed in.

# -----------------------------
# Valid extracted JSON fixture
# -----------------------------

@pytest.fixture
def valid_extracted_data():
    return {
        "data": {
            "symbol": "TEST.NS",
            "interval": "1d",
            "currency": "INR",
            "candles": [
                {
                    "date": "2026-07-01",
                    "open": 100,
                    "high": 110,
                    "low": 95,
                    "close": 105,
                    "adjclose": 105,
                    "volume": 1000,
                    "synthetic": False,
                },
                {
                    "date": "2026-07-02",
                    "open": 105,
                    "high": 115,
                    "low": 100,
                    "close": 110,
                    "adjclose": 110,
                    "volume": 1200,
                    "synthetic": False,
                },
                {
                    "date": "2026-07-03",
                    "open": 110,
                    "high": 120,
                    "low": 108,
                    "close": 118,
                    "adjclose": 118,
                    "volume": 1500,
                    "synthetic": False,
                },
            ],
        }
    }


# -----------------------------
# Malformed extracted JSON fixture
# -----------------------------

@pytest.fixture
def malformed_extracted_data():
    return {
        "data": {
            "symbol": "TEST.NS",
            "interval": "1d",
            "currency": "INR",
            "candles": [

                # Valid candle
                {
                    "date": "2026-07-01",
                    "open": 100,
                    "high": 110,
                    "low": 95,
                    "close": 105,
                    "adjclose": 105,
                    "volume": 1000,
                    "synthetic": False,
                },

                # Duplicate date
                {
                    "date": "2026-07-01",
                    "open": 101,
                    "high": 111,
                    "low": 96,
                    "close": 106,
                    "adjclose": 106,
                    "volume": 1100,
                    "synthetic": False,
                },

                # Missing close
                {
                    "date": "2026-07-02",
                    "open": 105,
                    "high": 115,
                    "low": 100,
                    "adjclose": 110,
                    "volume": 1200,
                    "synthetic": False,
                },

                # Invalid numeric close
                {
                    "date": "2026-07-06",
                    "open": 110,
                    "high": 120,
                    "low": 108,
                    "close": "n/a",
                    "adjclose": 118,
                    "volume": 1500,
                    "synthetic": False,
                },

                # High below low
                {
                    "date": "2026-07-07",
                    "open": 115,
                    "high": 100,
                    "low": 110,
                    "close": 112,
                    "adjclose": 112,
                    "volume": 1600,
                    "synthetic": False,
                },

                # Negative volume
                {
                    "date": "2026-07-08",
                    "open": 112,
                    "high": 120,
                    "low": 108,
                    "close": 115,
                    "adjclose": 115,
                    "volume": -500,
                    "synthetic": False,
                },

                # Invalid date format
                {
                    "date": "09/07/2026",
                    "open": 115,
                    "high": 125,
                    "low": 110,
                    "close": 120,
                    "adjclose": 120,
                    "volume": 1800,
                    "synthetic": False,
                },
            ],
        }
    }


# =============================
# VALID DATA TESTS
# =============================


def test_transform_valid_data(valid_extracted_data):
    """
    Valid candles should move into clean dataframe.
    No rows should be rejected.
    """

    clean_df, rejected_df = transform(valid_extracted_data)

    assert len(clean_df) == 3
    assert len(rejected_df) == 0



def test_transform_creates_expected_columns(valid_extracted_data):

    clean_df, rejected_df = transform(valid_extracted_data)

    expected_columns = [
        "symbol",
        "currency",
        "interval",
        "date",
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
        "synthetic",
        "daily_return",
        "daily_range",
        "daily_range_pct",
        "turnover",
    ]

    for column in expected_columns:
        assert column in clean_df.columns



def test_transform_preserves_values(valid_extracted_data):

    clean_df, rejected_df = transform(valid_extracted_data)

    row = clean_df.iloc[0]

    assert row["symbol"] == "TEST.NS"
    assert row["currency"] == "INR"
    assert row["interval"] == "1d"

    assert row["open"] == 100
    assert row["high"] == 110
    assert row["low"] == 95
    assert row["close"] == 105



# def test_transform_calculates_metrics(valid_extracted_data):

#     clean_df, rejected_df = transform(valid_extracted_data)

#     row = clean_df.iloc[0]

#     # high - low
#     assert row["daily_range"] == 15

#     # close * volume
#     assert row["turnover"] == 105000

def test_transform_calculates_metrics(valid_extracted_data):

    clean_df, _ = transform(valid_extracted_data)

    first = clean_df.iloc[0]

    assert first["daily_range"] == 15
    assert first["daily_range_pct"] == 0.15
    assert first["turnover"] == 105000

    second = clean_df.iloc[1]

    assert round(second["daily_return"], 4) == round(
        (110 - 105) / 105,
        4
    )


# =============================
# INVALID DATA TESTS
# =============================


def test_transform_rejects_duplicate_dates(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    assert "duplicate date" in (
        rejected_df["reason"].values
    )



def test_transform_rejects_missing_close(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    assert "missing close" in (
        rejected_df["reason"].values
    )



def test_transform_rejects_invalid_close(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    assert "invalid close" in (
        rejected_df["reason"].values
    )



def test_transform_rejects_high_below_low(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    assert "high below low" in (
        rejected_df["reason"].values
    )



def test_transform_rejects_negative_volume(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    assert "negative volume" in (
        rejected_df["reason"].values
    )



def test_transform_rejects_invalid_date(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    assert "invalid date" in (
        rejected_df["reason"].values
    )



def test_transform_clean_dataframe_has_no_invalid_rows(
        malformed_extracted_data
):

    clean_df, rejected_df = transform(
        malformed_extracted_data
    )

    # Every surviving row must satisfy analytical rules

    assert all(
        clean_df["high"] >= clean_df["low"]
    )

    assert all(
        clean_df["volume"].dropna() >= 0
    )

    assert all(
        clean_df["close"].apply(
            lambda x: isinstance(x, (int, float))
        )
    )

def test_transform_empty_dataset():

    empty_data = {
        "data": {
            "symbol": "TEST.NS",
            "interval": "1d",
            "currency": "INR",
            "candles": []
        }
    }

    clean_df, rejected_df = transform(empty_data)

    assert clean_df.empty
    assert rejected_df.empty