
//import com.yellow.enums.OrderStatus;
//
//import java.time.Instant;
//import java.util.List;
//
//public interface OrderMapper {
//    List<OrderHistoryRow> findByAccountId(Long accountId, OrderStatus status, Instant from, Instant to);
//}

package com.yellow.trade.mappers;

import com.yellow.entities.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OrderMapper {

    // Implemented in OrderMapper.xml — dynamic filters don't read well as annotations.
    int insertOrder(Order order);

    Order selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    List<Order> selectByAccountId(@Param("accountId") Long accountId,
                                  @Param("status") String status,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    // Guarded state transition, used by cancel (Story 5). Returns 0 if the order
    // was not in expectedStatus when the write ran — refuse with ORD-409, don't retry.
    int updateStatusConditional(@Param("idempotencyKey") String idempotencyKey,
                                @Param("newStatus") String newStatus,
                                @Param("expectedStatus") String expectedStatus);
}