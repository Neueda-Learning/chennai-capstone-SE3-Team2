-- =====================================================================
-- 016_strategy_indicators.sql
--
-- Sprint 10, the strategy module: the two indicator triggers the team's
-- acceptance criteria ask for (decision log 0017).
--
--   MA_CROSSOVER  a buy fires when the 20-day average crosses above the
--                 50-day; a sell when it crosses below.
--   BOLLINGER     a buy fires at the lower band, a sell at the upper.
--
-- Neither has a price of its own, so trigger_price is now NULL for them
-- and required for the two level triggers. Safe to run again: each
-- constraint is dropped, if it is there, before it is added.
-- =====================================================================

ALTER TABLE strat_strategy ALTER COLUMN trigger_price DROP NOT NULL;

ALTER TABLE strat_strategy DROP CONSTRAINT IF EXISTS ck_strat_trigger;
ALTER TABLE strat_strategy ADD CONSTRAINT ck_strat_trigger
    CHECK (trigger_kind IN ('FALLS_THROUGH', 'RISES_THROUGH', 'MA_CROSSOVER', 'BOLLINGER'));

ALTER TABLE strat_strategy DROP CONSTRAINT IF EXISTS ck_strat_positive;
ALTER TABLE strat_strategy ADD CONSTRAINT ck_strat_positive
    CHECK (quantity > 0 AND max_spend > 0 AND max_position > 0 AND (trigger_price IS NULL OR trigger_price > 0));

-- A level trigger has a price; an indicator trigger has none.
ALTER TABLE strat_strategy DROP CONSTRAINT IF EXISTS ck_strat_trigger_price;
ALTER TABLE strat_strategy ADD CONSTRAINT ck_strat_trigger_price
    CHECK ((trigger_kind IN ('FALLS_THROUGH', 'RISES_THROUGH')) = (trigger_price IS NOT NULL));
