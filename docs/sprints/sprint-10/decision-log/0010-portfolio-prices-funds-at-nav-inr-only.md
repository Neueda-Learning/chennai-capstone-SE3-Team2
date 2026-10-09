# 0010 Portfolio prices funds at their NAV, and totals in INR only

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

`Contracts/API Schemas/portfolio-api.yaml` prices from Fauxnance batch quotes. Our universe is every NSE
equity and every Direct Growth mutual fund; Fauxnance does not price funds, the MF NAV service
does. Every instrument is in INR. The contract: a team that cannot convert currencies "must
restrict its instrument universe to one currency and say so".

## Options considered

| Option | For | Against |
|---|---|---|
| Stocks from Fauxnance, funds from the MF NAV service, both through the platform's cached price layer; INR only | Every holding priced; one cache and one quota budget for the platform | Funds are priced from a source the contract does not name |
| Fauxnance only | Exactly the contract's source | Every fund holding unpriced and the summary always partial |

## Decision

Both sources, through the shared price layer. A portfolio that is always partial for anyone
holding a fund would fail the customer for the sake of the source's name.

## Consequences

`baseCurrency` is always INR. The deviation from the contract's named source is recorded here
and raised on day one.
