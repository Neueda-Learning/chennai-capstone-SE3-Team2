"""Tests for build-instrument-universe.py.  python3 -m unittest discover -s scripts"""

import importlib.util
import unittest
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "universe", Path(__file__).with_name("build-instrument-universe.py"))
universe = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(universe)

NSE = """SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE
MRF,MRF Limited,EQ,01-JAN-1995,10,1,INE883A01011,10
M&M,Mahindra & Mahindra Limited,EQ,01-JAN-1995,5,1,INE101A01026,5
NOISIN,No Isin Limited,EQ,01-JAN-2000,10,1,,10
ABCDEFGHIJKLMNOPQRSTUV,Too Long Limited,EQ,01-JAN-2000,10,1,INE000X01010,10
"""

AMFI = """Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date

Open Ended Schemes(Equity Scheme - Flexi Cap Fund)

PPFAS Mutual Fund

122639;INF879O01027;-;Parag Parikh Flexi Cap Fund;Direct Plan;Growth;88.25;05-Oct-2026
122640;INF879O01035;-;Parag Parikh Flexi Cap Fund;Regular Plan;Growth;80.10;05-Oct-2026
122641;INF879O01043;INF879O01050;Parag Parikh Flexi Cap Fund;Direct Plan;IDCW;30.00;05-Oct-2026
135764;INF846K01WR4;-;Axis Children's Fund;Direct Plan;Growth Option;29.66;05-Oct-2026
135765;-;-;Fund Without Isin;Direct Plan;Growth;10.00;05-Oct-2026

Close Ended Schemes(Income)

Axis Mutual Fund

140001;INF846K01ZZ9;-;Axis Fixed Term Plan;Direct Plan;Growth;11.00;05-Oct-2026
"""


class ParseNse(unittest.TestCase):
    def test_every_equity_with_an_isin_becomes_a_dot_ns_ticker(self):
        stocks = universe.parse_nse(NSE)
        self.assertEqual([s[0] for s in stocks], ["M&M.NS", "MRF.NS"])
        self.assertEqual(stocks[1], ("MRF.NS", "MRF Limited", "INE883A01011", 1))

    def test_a_symbol_the_order_contract_cannot_carry_is_left_out(self):
        tickers = [s[0] for s in universe.parse_nse(NSE)]
        self.assertNotIn("ABCDEFGHIJKLMNOPQRSTUV.NS", tickers)


class ParseAmfi(unittest.TestCase):
    def setUp(self):
        self.funds = universe.parse_amfi(AMFI)

    def test_only_open_ended_direct_growth_funds_with_an_isin(self):
        self.assertEqual([f[0] for f in self.funds], ["122639", "135764"])

    def test_the_name_says_the_plan_and_the_fund_house_is_kept(self):
        self.assertEqual(self.funds[0],
                         ("122639", "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                          "INF879O01027", "PPFAS Mutual Fund"))


class AmcCodes(unittest.TestCase):
    def test_a_seeded_fund_house_keeps_its_code(self):
        self.assertEqual(universe.amc_codes({"PPFAS Mutual Fund"}), {"PPFAS Mutual Fund": "PPFAS"})

    def test_codes_are_short_unique_and_stable(self):
        codes = universe.amc_codes({"Aditya Birla Sun Life Mutual Fund", "The Wealth Company Mutual Fund",
                                    "HDFC Mutual Fund"})
        self.assertEqual(codes["Aditya Birla Sun Life Mutual Fund"], "ADITYABIRLASUNLIFE")
        self.assertEqual(codes["HDFC Mutual Fund"], "HDFC")
        self.assertTrue(all(len(code) <= 20 for code in codes.values()))
        self.assertEqual(len(set(codes.values())), 3)


class Render(unittest.TestCase):
    def test_quotes_are_escaped_and_every_insert_is_idempotent(self):
        sql = universe.render(universe.parse_nse(NSE), universe.parse_amfi(AMFI), "2026-10-06")
        self.assertIn("'Axis Children''s Fund - Direct Plan - Growth'", sql)
        self.assertEqual(sql.count("INSERT INTO"), 5)
        self.assertEqual(sql.count("ON CONFLICT DO NOTHING"), 5)
        self.assertIn("'M&M.NS'", sql)

    def test_the_fund_house_rows_fill_every_column_they_name(self):
        sql = universe.render(universe.parse_nse(NSE), universe.parse_amfi(AMFI), "2026-10-06")
        self.assertIn("INSERT INTO amc (amc_code, amc_name) VALUES\n    ('PPFAS', 'PPFAS Mutual Fund')", sql)


if __name__ == "__main__":
    unittest.main()
