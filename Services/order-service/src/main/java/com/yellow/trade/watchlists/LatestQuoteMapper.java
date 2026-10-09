package com.yellow.trade.watchlists;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** watch_latest_quote: the last price market-data carried for each instrument. */
@Mapper
public interface LatestQuoteMapper {

    /**
     * Holds this quote unless one observed later is already held. 0 rows: it
     * arrived out of order, and changes nothing.
     */
    @Insert("""
            INSERT INTO watch_latest_quote (instrument_id, price, change_percent, stale, quote_as_of, event_id, received_at)
            VALUES (#{instrumentId}, #{price}, #{changePercent}, #{stale}, #{quoteAsOf}, #{eventId}, #{receivedAt})
            ON CONFLICT (instrument_id) DO UPDATE
               SET price = EXCLUDED.price, change_percent = EXCLUDED.change_percent, stale = EXCLUDED.stale,
                   quote_as_of = EXCLUDED.quote_as_of, event_id = EXCLUDED.event_id,
                   received_at = EXCLUDED.received_at
             WHERE watch_latest_quote.quote_as_of <= EXCLUDED.quote_as_of
            """)
    int hold(@Param("instrumentId") long instrumentId, @Param("price") BigDecimal price,
             @Param("changePercent") BigDecimal changePercent, @Param("stale") boolean stale,
             @Param("quoteAsOf") Instant quoteAsOf, @Param("eventId") UUID eventId,
             @Param("receivedAt") Instant receivedAt);
}
