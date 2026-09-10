package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * orders. Statements live in src/main/resources/mapper/OrderMapper.xml.
 *
 * XML rather than annotations for this one mapper because the history query
 * takes three optional filters, and <if> reads better than a string built in
 * Java -- which is also how a concatenated filter turns into an injection.
 * Every value is still bound as a parameter; the XML uses no interpolation.
 */
@Mapper
public interface OrderMapper {

    /**
     * Returns the affected row count. One means inserted. The unique index
     * uq_orders_client_idempotency_key can refuse the row instead, which
     * surfaces as a DuplicateKeyException the service turns into ORD-409 --
     * that constraint, not a read-then-write check, is the authority on
     * rule 8, because two concurrent requests carrying one key both pass a
     * read.
     */
    int insert(OrderRow row);

    OrderRow findById(@Param("orderId") UUID orderId);

    List<OrderRow> findByAccountId(@Param("accountId") Long accountId,
                                   @Param("status") String status,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    /**
     * Cancels in one statement, conditional on the status the caller expects.
     *
     * Reading the status and then writing it would let the executor fill the
     * order in between, and the cancel would overwrite a fill. Naming NEW in
     * the WHERE clause makes the database decide: one row affected means this
     * caller cancelled it, zero means somebody else got there first and the
     * answer is ORD-409.
     */
    int cancelIfNew(@Param("orderId") UUID orderId,
                    @Param("resolvedAt") Instant resolvedAt);
}
