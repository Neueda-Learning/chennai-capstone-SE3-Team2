package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrderMapper {
    int insert(OrderRow row);

    OrderRow findById(@Param("orderId") UUID orderId);

    List<OrderRow> findByAccountId(@Param("accountId") Long accountId,
                                   @Param("status") String status,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    int cancelIfNew(@Param("orderId") UUID orderId,
                    @Param("resolvedAt") Instant resolvedAt);

    int fillIfNew(@Param("orderId") UUID orderId,
                  @Param("fillPrice") java.math.BigDecimal fillPrice,
                  @Param("resolvedAt") Instant resolvedAt);
}
