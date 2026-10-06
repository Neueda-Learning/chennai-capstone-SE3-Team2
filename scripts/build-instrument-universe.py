#!/usr/bin/env python3
"""Build data/db/seed/008_instrument_universe.sql from the two public lists.

  NSE  every listed equity (EQUITY_L.csv)         -> instrument + equity, ticker SYMBOL.NS
  AMFI every open-ended Direct Plan Growth fund   -> amc + instrument + mutual_fund,
       (NAVAll.txt)                                  symbol = the AMFI scheme code

Fauxnance prices any NSE ticker on demand and the MF NAV service any AMFI
scheme code, so this list is what a customer can search and order. Re-run it
to refresh the snapshot; the output is committed, the downloads are not.

    python3 scripts/build-instrument-universe.py              # download both
    python3 scripts/build-instrument-universe.py --nse EQUITY_L.csv --amfi NAVAll.txt

Standard library only.
"""

import argparse
import csv
import io
import re
import sys
import urllib.request
from datetime import date
from pathlib import Path

NSE_URL = "https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv"
AMFI_URL = "https://portal.amfiindia.com/spages/NAVAll.txt"
OUT = Path(__file__).resolve().parent.parent / "data/db/seed/008_instrument_universe.sql"

# The order contract caps a symbol at 20 characters.
MAX_SYMBOL = 20

# Fund houses already seeded under a code of their own (seed/007). Reusing the
# code keeps one amc row per fund house.
KNOWN_AMC_CODES = {
    "ICICI Prudential Mutual Fund": "ICICIPRU",
    "PPFAS Mutual Fund": "PPFAS",
    "UTI Mutual Fund": "UTI",
    "HDFC Mutual Fund": "HDFC",
}

SECTION = re.compile(r"^(Open Ended Schemes|Close Ended Schemes|Interval Fund Schemes)")
NOT_GROWTH = re.compile(r"idcw|dividend|bonus|payout|reinvest", re.IGNORECASE)


def read_source(location):
    if re.match(r"https?://", location):
        request = urllib.request.Request(location, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read().decode("utf-8-sig")
    return Path(location).read_text(encoding="utf-8-sig")


def parse_nse(text):
    """Rows of (ticker, name, isin, lot_size), one per NSE equity with an ISIN."""
    stocks = []
    for row in csv.DictReader(io.StringIO(text)):
        row = {key.strip(): (value or "").strip() for key, value in row.items()}
        symbol, isin = row["SYMBOL"], row["ISIN NUMBER"]
        if not symbol or not isin.startswith("IN"):
            continue
        ticker = symbol + ".NS"
        if len(ticker) > MAX_SYMBOL:
            continue
        lot = row.get("MARKET LOT") or "1"
        stocks.append((ticker, row["NAME OF COMPANY"], isin, int(lot) if lot.isdigit() else 1))
    return sorted(stocks)


def is_direct_growth(plan, option, name):
    if plan:
        if plan.strip().lower() != "direct plan":
            return False
    elif not re.search(r"\bdirect\b", name, re.IGNORECASE):
        return False
    if option:
        return option.strip().lower() in ("growth", "growth option")
    return "growth" in name.lower() and not NOT_GROWTH.search(name)


def parse_amfi(text):
    """Rows of (scheme_code, name, isin, amc_name): open-ended Direct Growth funds."""
    section, amc, funds, seen_isins = None, None, [], set()
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if ";" not in line:
            if SECTION.match(line):
                section = SECTION.match(line).group(1)
            else:
                amc = line
            continue
        fields = line.split(";")
        if len(fields) < 6 or not fields[0].isdigit() or section != "Open Ended Schemes":
            continue
        code, isin, scheme, plan, option = fields[0], fields[1], fields[3], fields[4], fields[5]
        if not is_direct_growth(plan, option, scheme) or not isin.startswith("INF"):
            continue
        if isin in seen_isins:
            continue
        seen_isins.add(isin)
        name = f"{scheme} - Direct Plan - Growth" if plan else scheme
        funds.append((code, name, isin, amc))
    return sorted(funds, key=lambda fund: int(fund[0]))


def amc_codes(amc_names):
    """A stable, unique code of at most 20 characters per fund house."""
    codes, taken = {}, set(KNOWN_AMC_CODES.values())
    for name in sorted(amc_names):
        if name in KNOWN_AMC_CODES:
            codes[name] = KNOWN_AMC_CODES[name]
            continue
        base = re.sub(r"[^A-Z0-9]", "", name.upper().replace("MUTUAL FUND", ""))[:20] or "AMC"
        code, n = base, 2
        while code in taken:
            suffix = str(n)
            code, n = base[: 20 - len(suffix)] + suffix, n + 1
        taken.add(code)
        codes[name] = code
    return codes


def sql(value):
    if isinstance(value, int):
        return str(value)
    return "'" + value.replace("'", "''") + "'"


def values(rows):
    return ",\n".join("    (" + ", ".join(sql(v) for v in row) + ")" for row in rows)


def render(stocks, funds, generated_on):
    codes = amc_codes({fund[3] for fund in funds})
    return f"""-- 008_instrument_universe.sql
--
-- GENERATED by scripts/build-instrument-universe.py on {generated_on}. Do not
-- edit by hand: re-run the script to refresh.
--
-- Every NSE-listed equity ({len(stocks)}) and every open-ended Direct Plan Growth
-- mutual fund ({len(funds)}, from {len(codes)} fund houses), so a customer can find and
-- order any of them. Fauxnance prices an NSE ticker on demand; the MF NAV
-- service prices any AMFI scheme code.
--
-- Sources: {NSE_URL}
--          {AMFI_URL}
--
-- Idempotent: an instrument already present (same ISIN, ticker or scheme
-- code, e.g. seed/005 and seed/007) is left exactly as it is.

BEGIN;

INSERT INTO amc (amc_code, amc_name) VALUES
{values(sorted((code, name) for name, code in codes.items()))}
ON CONFLICT DO NOTHING;

INSERT INTO instrument (instrument_type, name, isin, is_tradable)
SELECT 'STOCK', v.name, v.isin, TRUE
FROM (VALUES
{values([(name, isin) for _, name, isin, _ in stocks])}
) AS v (name, isin)
ON CONFLICT DO NOTHING;

INSERT INTO equity (instrument_id, instrument_type, ticker, exchange_code, lot_size)
SELECT i.instrument_id, 'STOCK', v.ticker, 'NSE', v.lot_size
FROM (VALUES
{values([(ticker, isin, lot) for ticker, _, isin, lot in stocks])}
) AS v (ticker, isin, lot_size)
JOIN instrument i ON i.isin = v.isin AND i.instrument_type = 'STOCK'
ON CONFLICT DO NOTHING;

INSERT INTO instrument (instrument_type, name, isin, is_tradable)
SELECT 'MF', v.name, v.isin, TRUE
FROM (VALUES
{values([(name, isin) for _, name, isin, _ in funds])}
) AS v (name, isin)
ON CONFLICT DO NOTHING;

INSERT INTO mutual_fund (instrument_id, instrument_type, scheme_code, amc_id, plan_type, expense_ratio)
SELECT i.instrument_id, 'MF', v.scheme_code, a.amc_id, 'DIRECT_GROWTH', NULL
FROM (VALUES
{values([(code, isin, codes[amc]) for code, _, isin, amc in funds])}
) AS v (scheme_code, isin, amc_code)
JOIN instrument i ON i.isin = v.isin AND i.instrument_type = 'MF'
JOIN amc a ON a.amc_code = v.amc_code
ON CONFLICT DO NOTHING;

COMMIT;
"""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--nse", default=NSE_URL, help="EQUITY_L.csv path or URL")
    parser.add_argument("--amfi", default=AMFI_URL, help="NAVAll.txt path or URL")
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args(argv)

    stocks = parse_nse(read_source(args.nse))
    funds = parse_amfi(read_source(args.amfi))
    if not stocks or not funds:
        sys.exit(f"refusing to write an empty universe: {len(stocks)} stocks, {len(funds)} funds")

    Path(args.out).write_text(render(stocks, funds, date.today().isoformat()), encoding="utf-8")
    print(f"wrote {args.out}: {len(stocks)} stocks, {len(funds)} funds")


if __name__ == "__main__":
    main()
