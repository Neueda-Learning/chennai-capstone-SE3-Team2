# Claims

| # | Claim | Chart artefact |
|---|---|---|
| 1 | `Infosys (NSE: INFY)` was roughly 56% more volatile than `Reliance Industries (NSE: RELIANCE)` over the year from 26 Aug 2025 to 26 Aug 2026, with average daily price swings of 1.93% versus 1.24%. | `charts/claim1_volatility.png` |
| 2 | `Infosys (NSE: INFY)` had its largest single day price move of the past year on 4 Feb 2026, closing 7.26% away from the prior day's close. | `charts/claim2_infy_spike.png` |
| 3 | `Tata Steel (BSE: TATASTEEL)` averaged about 1.29 million shares traded per day over the 5-week window from 17 Jul to 26 Aug 2026, the only period for which candle data was available. | `charts/claim3_tatasteel_volume.png` |
| 4 | `For Infosys (NSE: INFY)` over the year from 26 Aug 2025 to 26 Aug 2026, higher daily trading volume was associated with larger same-day price swings (correlation r = [0.52]). | `charts/claim5_volume_vs_move.png` |

## Notes

**Date range pulled:** 
- `INFY.NS` and `RELIANCE.NS`: 26 Aug 2025 – 26 Aug 2026 (262 trading days each). 
- `TATASTEEL.BO`: from 17 Jul 2026 – 26 Aug 2026 (29 trading days), the API does not return a full year of
history for this symbol.

**Symbols in scope:** `INFY.NS (NSE)`, `RELIANCE.NS (NSE)`, `TATASTEEL.BO (BSE)`.

**How rejected/flagged rows were handled:** 
- Rows marked `synthetic: true` by the data provider 
     - (11 for INFY.NS, 11 for RELIANCE.NS, 2 for TATASTEEL.BO)
- Have `volume: null` and were excluded from every volume based calculation.
- Price fields on synthetic rows were kept, since they still form a continuous price series.

**Claims withdrawn:**
- A claim comparing volatility across all three symbols. 
  - Withdrawn because time window mismatch. TATASTEEL self contained.
- A claim about INFY's and RELIANCE's respective shares of combined daily
  volume (46% / 54%), shown as a pie chart. 
  - Withdrawn because the split is almost even
  - The chart-generating code for this (`claim4_volume_share_pie`) is left in `visualize_claims.py` but its output
  is not referenced in the claims table above.

**Running analytics pipeline:**
```
python eda.py                  # prints EDA metrics per symbol to console
python visualize_claims.py     # regenerates charts/*.png
```
**Requirements:**
`candle-data/{SYMBOL}-candle.json` to already exist for any instrument (fetched via the Fauxnance `/v1/candles/{symbol}`
endpoint).