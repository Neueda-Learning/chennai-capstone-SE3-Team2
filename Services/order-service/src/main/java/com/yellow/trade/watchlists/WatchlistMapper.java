package com.yellow.trade.watchlists;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** watch_list and watch_item: this module's tables, and only this module's. */
@Mapper
public interface WatchlistMapper {

    /**
     * Serialises one account's changes for the length of the caller's
     * transaction, so two requests at once cannot both slip under a cap.
     * Keyed by this migration's number and the account.
     */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(1013, CAST(#{clientId} AS integer))) locked")
    Integer lockAccount(@Param("clientId") long clientId);

    @Select("""
            SELECT watchlist_id, client_id, name, position
            FROM watch_list
            WHERE client_id = #{clientId}
            ORDER BY position, watchlist_id
            """)
    List<WatchlistRow> findForClient(@Param("clientId") long clientId);

    /** This account's watchlist, or null: another account's is not found, the same as one that does not exist. */
    @Select("""
            SELECT watchlist_id, client_id, name, position
            FROM watch_list
            WHERE watchlist_id = #{watchlistId} AND client_id = #{clientId}
            """)
    WatchlistRow findOwned(@Param("clientId") long clientId, @Param("watchlistId") long watchlistId);

    @Select("SELECT count(*) FROM watch_list WHERE client_id = #{clientId}")
    int countForClient(@Param("clientId") long clientId);

    @Insert("""
            INSERT INTO watch_list (client_id, name, position)
            VALUES (#{clientId}, #{name},
                    (SELECT COALESCE(max(position), 0) + 1 FROM watch_list WHERE client_id = #{clientId}))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "watchlistId", keyColumn = "watchlist_id")
    int insert(WatchlistRow row);

    @Update("UPDATE watch_list SET name = #{name} WHERE watchlist_id = #{watchlistId} AND client_id = #{clientId}")
    int rename(@Param("clientId") long clientId, @Param("watchlistId") long watchlistId, @Param("name") String name);

    @Delete("DELETE FROM watch_list WHERE watchlist_id = #{watchlistId} AND client_id = #{clientId}")
    int delete(@Param("clientId") long clientId, @Param("watchlistId") long watchlistId);

    /** Every entry of every one of the account's watchlists, in order, with the latest quote held for each. */
    @Select("""
            SELECT wi.watchlist_id,
                   COALESCE(e.ticker, m.scheme_code) AS symbol,
                   wi.position,
                   q.price          AS last_price,
                   q.change_percent AS change_percent,
                   q.quote_as_of    AS price_as_of,
                   COALESCE(q.stale, false) AS stale
            FROM watch_item wi
            JOIN watch_list wl            ON wl.watchlist_id = wi.watchlist_id
            JOIN instrument i             ON i.instrument_id = wi.instrument_id
            LEFT JOIN equity e            ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m       ON m.instrument_id = i.instrument_id
            LEFT JOIN watch_latest_quote q ON q.instrument_id = wi.instrument_id
            WHERE wl.client_id = #{clientId}
            ORDER BY wi.watchlist_id, wi.position
            """)
    List<ItemRow> findItemsForClient(@Param("clientId") long clientId);

    @Select("SELECT count(*) FROM watch_item WHERE watchlist_id = #{watchlistId}")
    int countItems(@Param("watchlistId") long watchlistId);

    /** Adds to the end. Already there: nothing changes, 0 rows. */
    @Insert("""
            INSERT INTO watch_item (watchlist_id, instrument_id, position)
            VALUES (#{watchlistId}, #{instrumentId},
                    (SELECT COALESCE(max(position), 0) + 1 FROM watch_item WHERE watchlist_id = #{watchlistId}))
            ON CONFLICT (watchlist_id, instrument_id) DO NOTHING
            """)
    int insertItem(@Param("watchlistId") long watchlistId, @Param("instrumentId") long instrumentId);

    @Select("SELECT count(*) > 0 FROM watch_item WHERE watchlist_id = #{watchlistId} AND instrument_id = #{instrumentId}")
    boolean hasItem(@Param("watchlistId") long watchlistId, @Param("instrumentId") long instrumentId);

    @Delete("DELETE FROM watch_item WHERE watchlist_id = #{watchlistId} AND instrument_id = #{instrumentId}")
    int deleteItem(@Param("watchlistId") long watchlistId, @Param("instrumentId") long instrumentId);
}
